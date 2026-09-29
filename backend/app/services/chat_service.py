from datetime import datetime, timezone
from threading import BoundedSemaphore
from sqlalchemy import func, select
from app.core.exceptions import AppError
from app.models import Conversation, Message, MessageSource
from app.rag import pipeline
from app.services.catalog_service import resolved_bot
from app.repositories import conversation_repository
from app.schemas.conversation import AnswerResponse
import logging
logger = logging.getLogger(__name__)

GENERATION_SLOT = BoundedSemaphore(1)


def ask(db, bot, data, *, conversation=None, channel="PLAYGROUND", integration_id=None, session_hash=None, commit=True):
    if bot.status != 'ACTIVE':
        raise AppError(409, 'This chatbot is currently disabled.')
    if not GENERATION_SLOT.acquire(blocking=False):
        raise AppError(429, 'Another answer is being generated. Please retry shortly.')
    try:
        history = [{'role': message.role.lower(), 'content': message.content[:1000]}
                   for message in conversation_repository.recent_messages(db, conversation.id)] if conversation else []
        result = pipeline.answer(db, resolved_bot(db, bot), data.question, history)
        if conversation is None:
            conversation = Conversation(chatbot_id=bot.id, title=data.question[:120], channel=channel, integration_id=integration_id, session_hash=session_hash)
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
                sheet_name=source.sheet_name, row_number=source.row_number, document_id=source.document_id, document_name=source.document_name, page_number=source.page_number,
                chunk_index=source.chunk_index, excerpt=source.excerpt, score=source.score))
        conversation.updated_at = datetime.now(timezone.utc)
        if commit:
            db.commit()
        else:
            db.flush()
        return AnswerResponse(**result.model_dump(), conversation_id=conversation.id,
                              user_message_id=user_message.id, message_id=assistant_message.id)
    except Exception:
        logger.warning("Chat request failed chatbot_id=%s channel=%s", bot.id, channel)
        db.rollback()
        raise
    finally:
        GENERATION_SLOT.release()
