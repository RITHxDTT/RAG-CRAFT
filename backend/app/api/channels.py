from uuid import UUID
from fastapi import APIRouter, Depends, Request, Response
from app.api.dependencies import Authenticated, DB, require_csrf
from app.schemas.channels import ChannelCreate, ChannelUpdate, ChannelView, PublicQuestion
from app.services import channel_service as service
from app.core.rate_limit import LoginLimiter
from app.core.exceptions import AppError
router=APIRouter(prefix='/api/chatbots/{chatbot_id}/channels',tags=['channels'],dependencies=[Depends(require_csrf)])
public_router=APIRouter(prefix='/api/public',tags=['public chat'])
public_limiter=LoginLimiter(maximum=30, message="Too many messages. Wait one minute before trying again.")

@router.get('',response_model=list[ChannelView])
def list_channels(chatbot_id:UUID,db:DB,user:Authenticated):
    return service.list_channels(db,user,chatbot_id)

@router.post('',response_model=ChannelView,status_code=201)
def create(chatbot_id:UUID,data:ChannelCreate,db:DB,user:Authenticated):
    return service.create(db,user,chatbot_id,data)

@router.patch('/{channel_id}',response_model=ChannelView)
def update(chatbot_id:UUID,channel_id:UUID,data:ChannelUpdate,db:DB,user:Authenticated):
    return service.update(db,user,chatbot_id,channel_id,data.enabled)

@router.post('/{channel_id}/regenerate',response_model=ChannelView)
def regenerate(chatbot_id:UUID,channel_id:UUID,db:DB,user:Authenticated):
    return service.regenerate(db,user,chatbot_id,channel_id)

@router.delete('/{channel_id}',status_code=204)
def disconnect(chatbot_id:UUID,channel_id:UUID,db:DB,user:Authenticated):
    service.disconnect(db,user,chatbot_id,channel_id)

@router.post('/{channel_id}/test',response_model=ChannelView)
def test(chatbot_id:UUID,channel_id:UUID,db:DB,user:Authenticated):
    from app.services.telegram_service import test_connection
    row=service.owned(db,user,chatbot_id,channel_id)
    if row.channel!='TELEGRAM':
        raise AppError(422,'Only Telegram connections need a connection test.')
    test_connection(db,row)
    return service.view(row)

@public_router.get('/{kind}/{public_id}')
def metadata(kind:str,public_id:str,db:DB,response:Response):
    if kind not in ('share','widget'):
        raise AppError(404,'Chat unavailable.')
    response.headers['Cache-Control']='no-store'
    return service.metadata(db,kind,public_id)

@public_router.post('/{kind}/{public_id}/ask')
def ask(kind:str,public_id:str,data:PublicQuestion,db:DB,request:Request,response:Response):
    if kind not in ('share','widget'):
        raise AppError(404,'Chat unavailable.')
    public_limiter.check(request.client.host if request.client else 'unknown')
    response.headers['Cache-Control']='no-store'
    return service.public_ask(db,kind,public_id,data)
