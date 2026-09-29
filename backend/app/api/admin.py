from uuid import UUID
from fastapi import APIRouter,Depends,Query
from app.api.dependencies import Admin,Authenticated,DB,require_csrf
from app.services import admin_service as service
from app.repositories import admin_repository as repository
from app.schemas.admin import UserView,UserStatus
router=APIRouter(prefix='/api',tags=['administration'],dependencies=[Depends(require_csrf)])

@router.get('/admin/users',response_model=list[UserView])
def users(db:DB,admin:Admin,search:str=Query('',max_length=120),limit:int=Query(100,ge=1,le=100),offset:int=Query(0,ge=0)):
    return service.list_users(db,search,limit,offset)

@router.get('/admin/users/{user_id}',response_model=UserView)
def user_detail(user_id:UUID,db:DB,admin:Admin):
    return service.detail(db,user_id)

@router.patch('/admin/users/{user_id}',response_model=UserView)
def status(user_id:UUID,data:UserStatus,db:DB,admin:Admin):
    return service.set_status(db,admin,user_id,data.is_active)

@router.get('/admin/chatbots')
def monitor(db:DB,admin:Admin,limit:int=Query(100,ge=1,le=100),offset:int=Query(0,ge=0)):
    return service.monitor(db,limit,offset)

@router.get('/admin/analytics')
def admin_analytics(db:DB,admin:Admin):
    return repository.analytics(db)

@router.get('/analytics')
def user_analytics(db:DB,user:Authenticated):
    return repository.analytics(db,user.user.id)
