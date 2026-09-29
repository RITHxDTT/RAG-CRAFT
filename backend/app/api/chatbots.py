from uuid import UUID
from fastapi import APIRouter, Depends
from app.api.dependencies import Authenticated, DB, require_csrf
from app.schemas.chatbot import ChatbotCreate, ChatbotUpdate, ChatbotView, Dashboard
from app.services import chatbot_service

router = APIRouter(prefix="/api", tags=["chatbots"], dependencies=[Depends(require_csrf)])


@router.get("/dashboard", response_model=Dashboard)
def dashboard(db: DB, admin: Authenticated):
    return chatbot_service.dashboard(db, admin)


@router.get("/chatbots", response_model=list[ChatbotView])
def list_chatbots(db: DB, admin: Authenticated):
    return chatbot_service.list_chatbots(db, admin)


@router.post("/chatbots", response_model=ChatbotView, status_code=201)
def create_chatbot(data: ChatbotCreate, db: DB, admin: Authenticated):
    return chatbot_service.create_chatbot(db, admin, data)


@router.get("/chatbots/{chatbot_id}", response_model=ChatbotView)
def get_chatbot(chatbot_id: UUID, db: DB, admin: Authenticated):
    return chatbot_service.view_chatbot(db, admin, chatbot_id)


@router.patch("/chatbots/{chatbot_id}", response_model=ChatbotView)
def update_chatbot(chatbot_id: UUID, data: ChatbotUpdate, db: DB, admin: Authenticated):
    return chatbot_service.update_chatbot(db, admin, chatbot_id, data)


@router.delete("/chatbots/{chatbot_id}", status_code=204)
def delete_chatbot(chatbot_id: UUID, db: DB, admin: Authenticated):
    chatbot_service.delete_chatbot(db, admin, chatbot_id)
