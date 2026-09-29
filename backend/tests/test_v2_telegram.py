from cryptography.fernet import Fernet
from pydantic import SecretStr
from sqlalchemy import select
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.models.channels import ChannelIntegration
from app.models import Conversation,Message
from app.schemas.rag import RagAnswer
from app.services import telegram_service,chat_service


def test_telegram_connect_webhook_deduplication_and_disable(logged_in,db,monkeypatch):
    config=get_settings()
    monkeypatch.setattr(config,'integration_encryption_key',SecretStr(Fernet.generate_key().decode()))
    monkeypatch.setattr(config,'public_api_url','https://api.example.test')
    calls=[]
    def request(token,method,data=None):
        calls.append((method,data))
        return {'id':123,'is_bot':True,'username':'hr_bot'} if method=='getMe' else True
    monkeypatch.setattr(telegram_service,'request',request)
    seen=[]
    def answer(db,bot,*args):
        seen.append(bot.id);return RagAnswer(answer='18 days',sources=[])
    monkeypatch.setattr(chat_service.pipeline,'answer',answer)
    bot=logged_in.post('/api/chatbots',json={'name':'HR'}).json()
    base=f"/api/chatbots/{bot['id']}/channels"
    token='123:private-bot-token'
    response=logged_in.post(base,json={'channel':'TELEGRAM','token':token})
    assert response.status_code==201
    assert token not in response.text
    assert logged_in.delete(f"/api/chatbots/{bot['id']}").status_code==409
    row=db.scalar(select(ChannelIntegration))
    assert row.credential!=token and token not in row.credential
    path='/api/webhooks/telegram/'+row.public_id
    data={'update_id':1,'message':{'chat':{'id':77,'type':'private'},'text':'Leave?'}}
    assert logged_in.post(path,json=data).status_code==403
    for _ in range(2):
        assert logged_in.post(path,json=data,headers={'X-Telegram-Bot-Api-Secret-Token':row.webhook_secret}).status_code==200
    assert len(seen)==1 and str(seen[0])==bot['id']
    assert len(list(db.scalars(select(Message))))==2
    assert db.scalar(select(Conversation)).channel=='TELEGRAM'
    assert sum(method=='sendMessage' for method,_ in calls)==1
    assert logged_in.patch(base+'/'+str(row.id),json={'enabled':False}).status_code==200
    assert logged_in.post(path,json=data,headers={'X-Telegram-Bot-Api-Secret-Token':row.webhook_secret}).status_code==404


def test_invalid_telegram_token_not_saved(logged_in,db,monkeypatch):
    monkeypatch.setattr(get_settings(),'integration_encryption_key',SecretStr(Fernet.generate_key().decode()))
    monkeypatch.setattr(get_settings(),'public_api_url','https://api.example.test')
    def invalid(*args):raise AppError(422,'The Telegram bot token is invalid.')
    monkeypatch.setattr(telegram_service,'request',invalid)
    bot=logged_in.post('/api/chatbots',json={'name':'HR'}).json()
    assert logged_in.post(f"/api/chatbots/{bot['id']}/channels",json={'channel':'TELEGRAM','token':'123:invalid'}).status_code==422
    assert db.scalar(select(ChannelIntegration)) is None
