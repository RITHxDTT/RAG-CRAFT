from uuid import UUID
from fastapi import APIRouter, Depends
from app.api.dependencies import Admin, DB, require_csrf
from app.schemas.rag import Question
from app.schemas.conversation import AnswerResponse, ConversationDetail, ConversationView
from app.services import playground_service, conversation_service

router = APIRouter(prefix='/api/chatbots/{chatbot_id}', tags=['playground'], dependencies=[Depends(require_csrf)])


@router.post('/ask', response_model=AnswerResponse)
def ask(chatbot_id: UUID, data: Question, db: DB, admin: Admin):
    return playground_service.ask(db, admin, chatbot_id, data)


@router.get('/conversations', response_model=list[ConversationView])
def list_conversations(chatbot_id: UUID, db: DB, admin: Admin):
    return conversation_service.list_conversations(db, admin, chatbot_id)


@router.get('/conversations/{conversation_id}', response_model=ConversationDetail)
def conversation(chatbot_id: UUID, conversation_id: UUID, db: DB, admin: Admin):
    return conversation_service.detail(db, admin, chatbot_id, conversation_id)


@router.delete('/conversations/{conversation_id}', status_code=204)
def delete_conversation(chatbot_id: UUID, conversation_id: UUID, db: DB, admin: Admin):
    conversation_service.delete_conversation(db, admin, chatbot_id, conversation_id)
