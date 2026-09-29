import hashlib
import html
import logging
import secrets
from sqlalchemy import select
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.models import Chatbot, User
from app.models.channels import ChannelIntegration
from app.repositories import channel_repository as channels
from app.schemas.channels import ChannelView
from app.services.chatbot_service import require_chatbot
from app.services import chat_service

logger=logging.getLogger(__name__)
KINDS={'share':'PUBLIC_LINK','widget':'WEB_WIDGET','telegram':'TELEGRAM'}

def view(row):
    base=get_settings().frontend_url.rstrip('/')
    url=f'{base}/{"share" if row.channel=="PUBLIC_LINK" else "widget"}/{row.public_id}' if row.channel!='TELEGRAM' else None
    embed=f'<script src="{html.escape(base,quote=True)}/widget.js" data-widget-id="{row.public_id}" defer></script>' if row.channel=='WEB_WIDGET' else None
    return ChannelView(id=row.id,channel=row.channel,public_id=row.public_id,enabled=row.enabled,status=row.status,url=url,embed_code=embed,
        telegram_username=row.telegram_username,token_mask='••••••••' if row.credential else None)

def list_channels(db,ctx,chatbot_id):
    require_chatbot(db,ctx,chatbot_id)
    return [view(row) for row in channels.list_channels(db,chatbot_id)]

def create(db,ctx,chatbot_id,data):
    require_chatbot(db,ctx,chatbot_id,lock=True)
    if any(row.channel==data.channel for row in channels.list_channels(db,chatbot_id)):
        raise AppError(409,'This channel already exists. Manage the existing connection.')
    row=ChannelIntegration(chatbot_id=chatbot_id,channel=data.channel,public_id=secrets.token_urlsafe(32))
    if data.channel=='TELEGRAM':
        from app.services.telegram_service import connect
        connect(db,row,data.token)
    db.add(row)
    db.commit()
    logger.info('Channel created chatbot_id=%s channel=%s',chatbot_id,data.channel)
    return view(row)

def owned(db,ctx,chatbot_id,channel_id):
    require_chatbot(db,ctx,chatbot_id,lock=True)
    row=db.get(ChannelIntegration,channel_id)
    if not row or row.chatbot_id!=chatbot_id:
        raise AppError(404,'Channel not found.')
    return row

def update(db,ctx,chatbot_id,channel_id,enabled):
    row=owned(db,ctx,chatbot_id,channel_id)
    if row.channel=='TELEGRAM':
        from app.services.telegram_service import set_enabled
        set_enabled(row,enabled)
    row.enabled=enabled
    db.commit()
    logger.info('Channel updated channel_id=%s enabled=%s',row.id,enabled)
    return view(row)

def regenerate(db,ctx,chatbot_id,channel_id):
    row=owned(db,ctx,chatbot_id,channel_id)
    if row.channel=='TELEGRAM':
        raise AppError(422,'Disconnect and reconnect Telegram to change the webhook.')
    row.public_id=secrets.token_urlsafe(32)
    db.commit()
    logger.info('Channel identifier regenerated channel_id=%s',row.id)
    return view(row)

def disconnect(db,ctx,chatbot_id,channel_id):
    row=owned(db,ctx,chatbot_id,channel_id)
    if row.channel=='TELEGRAM':
        from app.services.telegram_service import set_enabled
        set_enabled(row,False)
    db.delete(row)
    db.commit()
    logger.info('Channel disconnected chatbot_id=%s',chatbot_id)

def resolve(db,kind,public_id,*,lock=False):
    row=channels.public_channel(db,KINDS.get(kind,''),public_id)
    if not row:
        raise AppError(404,'This chat link is unavailable.')
    query=select(Chatbot).where(Chatbot.id==row.chatbot_id)
    if lock:
        query=query.with_for_update()
    bot=db.scalar(query)
    # Refresh after acquiring the same chatbot lock used by channel mutations.
    db.refresh(row)
    owner=db.get(User,bot.owner_id) if bot else None
    if row.public_id!=public_id or not row.enabled or not bot or bot.status!='ACTIVE' or not owner or not owner.is_active:
        raise AppError(404,'This chat link is unavailable.')
    return row,bot

def metadata(db,kind,public_id):
    _,bot=resolve(db,kind,public_id)
    return {'name':bot.name,'description':bot.description,'starter_questions':bot.starter_questions}

def public_ask(db,kind,public_id,data):
    row,bot=resolve(db,kind,public_id,lock=True)
    session=data.session_token or secrets.token_urlsafe(32)
    digest=hashlib.sha256(session.encode()).hexdigest()
    conversation=None
    if data.conversation_id:
        conversation=channels.guest_conversation(db,bot.id,row.id,digest,data.conversation_id)
        if not conversation or conversation.channel!=row.channel:
            raise AppError(404,'Conversation unavailable. Start a new conversation.')
    result=chat_service.ask(db,bot,data,conversation=conversation,channel=row.channel,integration_id=row.id,session_hash=digest)
    # Guests receive citation content, never private document download identifiers.
    payload=result.model_dump(mode='json')
    for source in payload['sources']:
        source['chunk_id']=source['document_id']=None
    return {**payload,'session_token':session}
