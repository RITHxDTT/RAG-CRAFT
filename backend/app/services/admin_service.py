from sqlalchemy import select,func
from app.models import User,Chatbot
from app.core.exceptions import AppError
from app.repositories import admin_repository as repository
from app.schemas.admin import UserView


def user_view(user,count):
    return UserView(id=user.id,full_name=user.full_name,email=user.email,role=user.role,is_active=user.is_active,chatbot_count=count,created_at=user.created_at)

def list_users(db,search,limit,offset):
    return [user_view(user,count) for user,count in repository.users(db,search,limit,offset)]

def detail(db,user_id):
    user=db.get(User,user_id)
    if not user:
        raise AppError(404,'User not found.')
    count=db.scalar(select(func.count()).select_from(Chatbot).where(Chatbot.owner_id==user_id))
    return user_view(user,count)

def set_status(db,ctx,user_id,active):
    user=db.scalar(select(User).where(User.id==user_id).with_for_update())
    if not user:
        raise AppError(404,'User not found.')
    if user.role=='ADMIN' and not active:
        raise AppError(409,'Platform administrators cannot be disabled here.')
    user.is_active=active
    user.token_version+=1
    db.commit()
    return detail(db,user_id)

def monitor(db,limit,offset):
    return [{'id':bot.id,'name':bot.name,'status':bot.status,'owner_email':email,'owner_name':name,'created_at':bot.created_at} for bot,email,name in repository.monitored_chatbots(db,limit,offset)]
