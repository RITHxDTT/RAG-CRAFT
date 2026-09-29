from fastapi import APIRouter, Request, Body
from app.api.dependencies import DB
from app.services import telegram_service
router=APIRouter(prefix='/api/webhooks/telegram',tags=['telegram webhook'])

@router.post('/{public_id}')
def webhook(public_id:str,request:Request,db:DB,data:dict=Body(...)):
    return telegram_service.webhook(db,public_id,request.headers.get('X-Telegram-Bot-Api-Secret-Token'),data)
