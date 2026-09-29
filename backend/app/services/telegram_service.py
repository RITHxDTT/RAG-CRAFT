"""Telegram transport adapter. Credentials never leave this module unencrypted."""
import hashlib
import logging
import secrets
import httpx
from cryptography.fernet import Fernet, InvalidToken
from sqlalchemy import select
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.models.channels import ChannelIntegration, TelegramUpdate
from app.repositories import channel_repository
from app.schemas.rag import Question
from app.services import chat_service

logger=logging.getLogger(__name__)

def cipher():
    key=get_settings().integration_encryption_key
    if not key:
        raise AppError(503,'Telegram encryption is not configured. Contact the administrator.')
    try:
        return Fernet(key.get_secret_value().encode())
    except (ValueError,TypeError):
        raise AppError(503,'Telegram encryption configuration is invalid.') from None

def credential(row):
    try:
        return cipher().decrypt(row.credential.encode()).decode()
    except (InvalidToken,AttributeError):
        raise AppError(503,'Telegram credentials are unavailable. Reconnect this bot.') from None

def request(token,method,data=None):
    # Telegram's own protocol requires the credential in its HTTPS path.
    # It is never included in our public URLs, errors, or log records.
    try:
        with httpx.Client(timeout=20) as client:
            response=client.post('https://api.telegram.org/bot'+token+'/'+method,json=data or {})
        if response.status_code in (401,404):
            raise AppError(422,'The Telegram bot token is invalid.')
        response.raise_for_status()
        body=response.json()
        if not body.get('ok'):
            raise AppError(502,'Telegram rejected the request. Check the bot configuration.')
        return body['result']
    except AppError:
        raise
    except (httpx.HTTPError,ValueError,KeyError):
        raise AppError(502,'Telegram is temporarily unavailable. Please retry.') from None

def webhook_url(row):
    base=get_settings().public_api_url.rstrip('/')
    if not base.startswith('https://'):
        raise AppError(422,'Telegram requires a public HTTPS API URL. Configure PUBLIC_API_URL first.')
    return base+'/api/webhooks/telegram/'+row.public_id

def connect(db,row,token):
    if not token:
        raise AppError(422,'Enter the bot token from BotFather.')
    encrypted=cipher().encrypt(token.encode()).decode()
    url=webhook_url(row)
    info=request(token,'getMe')
    if not info.get('is_bot'):
        raise AppError(422,'This Telegram account is not a bot.')
    bot_id=str(info['id'])
    if db.scalar(select(ChannelIntegration).where(ChannelIntegration.telegram_bot_id==bot_id)):
        raise AppError(409,'This Telegram bot is already connected.')
    row.credential=encrypted
    row.webhook_secret=secrets.token_urlsafe(32)
    row.telegram_bot_id=bot_id
    row.telegram_username=info.get('username')
    row.enabled=True
    row.status='CONNECTED'
    db.add(row)
    db.flush() # Reserve unique bot ID before making an external registration.
    request(token,'setWebhook',{'url':url,'secret_token':row.webhook_secret,'allowed_updates':['message'],'max_connections':1})
    logger.info('Telegram connected integration_id=%s',row.id)

def set_enabled(row,enabled):
    token=credential(row)
    if enabled:
        request(token,'setWebhook',{'url':webhook_url(row),'secret_token':row.webhook_secret,'allowed_updates':['message'],'max_connections':1})
    else:
        request(token,'deleteWebhook',{'drop_pending_updates':True})
    row.status='CONNECTED' if enabled else 'DISCONNECTED'

def test_connection(db,row):
    try:
        request(credential(row),'getMe')
        info=request(credential(row),'getWebhookInfo')
        row.status='CONNECTED' if row.enabled and info.get('url')==webhook_url(row) and not info.get('last_error_message') else ('DISCONNECTED' if not row.enabled else 'FAILED')
        db.commit()
    except AppError:
        row.status='FAILED'
        db.commit()
        raise

def webhook(db,public_id,secret,data):
    from app.services.channel_service import resolve
    row=channel_repository.public_channel(db,'TELEGRAM',public_id)
    if not row or not secret or not secrets.compare_digest(row.webhook_secret or '',secret):
        raise AppError(403,'Invalid webhook credentials.')
    row,bot=resolve(db,'telegram',public_id,lock=True)
    message=data.get('message') or {}
    if not isinstance(message, dict):
        return {'ok': True}
    text=message.get('text')
    chat=message.get('chat') or {}
    if not isinstance(chat, dict):
        return {'ok': True}
    update_id=data.get('update_id')
    if not isinstance(update_id,int) or not isinstance(text,str) or not isinstance(chat.get('id'),int):
        return {'ok':True}
    # Private chats avoid leaking a group's conversation context between participants.
    if chat.get('type')!='private':
        return {'ok':True}
    if not text.strip():
        return {'ok':True}
    record=db.scalar(select(TelegramUpdate).where(TelegramUpdate.integration_id==row.id,TelegramUpdate.update_id==update_id))
    try:
        if record is None:
            digest=hashlib.sha256(str(chat['id']).encode()).hexdigest()
            conversation=channel_repository.guest_conversation(db,bot.id,row.id,digest)
            result=chat_service.ask(db,bot,Question(question=text[:2000]),conversation=conversation,channel='TELEGRAM',integration_id=row.id,session_hash=digest,commit=False)
            answer=result.answer
            if result.sources:
                answer+='\n\nSources: '+', '.join(s.document_name+(f' p. {s.page_number}' if s.page_number else '') for s in result.sources)
            record=TelegramUpdate(integration_id=row.id,update_id=update_id,answer=answer)
            db.add(record)
            db.commit() # Persist the answer before delivery; retries do not regenerate.
        # Serialize deliveries for this update across webhook retries.
        record=db.scalar(select(TelegramUpdate).where(TelegramUpdate.id==record.id).with_for_update().execution_options(populate_existing=True))
        parts=[record.answer[i:i+3000] for i in range(0,len(record.answer),3000)]
        for index in range(record.sent_parts,len(parts)):
            request(credential(row),'sendMessage',{'chat_id':chat['id'],'text':parts[index]})
            record.sent_parts=index+1
            db.flush()
        db.commit()
        return {'ok':True}
    except Exception:
        db.rollback()
        logger.warning('Telegram webhook failed integration_id=%s',row.id)
        raise
