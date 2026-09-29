from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, ConfigDict
from app.schemas.rag import RagAnswer, Source


class ConversationView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    chatbot_id: UUID
    channel: str = "PLAYGROUND"
    title: str
    created_at: datetime
    updated_at: datetime


class MessageView(BaseModel):
    id: UUID
    role: str
    content: str
    created_at: datetime
    sources: list[Source]


class ConversationDetail(ConversationView):
    messages: list[MessageView]


class AnswerResponse(RagAnswer):
    conversation_id: UUID
    user_message_id: UUID
    message_id: UUID
