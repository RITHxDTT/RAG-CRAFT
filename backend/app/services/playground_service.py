from datetime import datetime, timezone
from threading import BoundedSemaphore
from sqlalchemy import func, select
from app.core.exceptions import AppError
from app.models import Conversation, Message, MessageSource
from app.rag import pipeline
from app.repositories import conversation_repository
from app.schemas.conversation import AnswerResponse
from app.services.chatbot_service import require_chatbot
from app.services.conversation_service import require_conversation

GENERATION_SLOT = BoundedSemaphore(1)


def ask(db, ctx, chatbot_id, data):
    # Holding the parent lock keeps document/settings mutations and message ordering consistent for the turn.
    bot = require_chatbot(db, ctx, chatbot_id, lock=True)
    if bot.status != 'ACTIVE':
        raise AppError(409, 'Activate this chatbot before using the Playground.')
    conversation = require_conversation(db, ctx, chatbot_id, data.conversation_id) if data.conversation_id else None
    if not GENERATION_SLOT.acquire(blocking=False):
        raise AppError(429, 'Another answer is being generated. Please retry shortly.')
    try:
        history = [{'role': message.role.lower(), 'content': message.content[:1000]}
                   for message in conversation_repository.recent_messages(db, conversation.id)] if conversation else []
        result = pipeline.answer(db, bot, data.question, history)
        if conversation is None:
            conversation = Conversation(chatbot_id=chatbot_id, title=data.question[:120])
            db.add(conversation)
            db.flush()
        last = db.scalar(select(func.max(Message.sequence)).where(Message.conversation_id == conversation.id))
        sequence = 0 if last is None else last + 1
        user_message = Message(conversation_id=conversation.id, sequence=sequence, role='USER', content=data.question)
        assistant_message = Message(conversation_id=conversation.id, sequence=sequence + 1, role='ASSISTANT', content=result.answer)
        db.add_all([user_message, assistant_message])
        db.flush()
        for source in result.sources:
            db.add(MessageSource(message_id=assistant_message.id, document_chunk_id=source.chunk_id,
                document_id=source.document_id, document_name=source.document_name, page_number=source.page_number,
                chunk_index=source.chunk_index, excerpt=source.excerpt, score=source.score))
        conversation.updated_at = datetime.now(timezone.utc)
        db.commit()
        return AnswerResponse(**result.model_dump(), conversation_id=conversation.id,
                              user_message_id=user_message.id, message_id=assistant_message.id)
    except Exception:
        db.rollback()
        raise
    finally:
        GENERATION_SLOT.release()
