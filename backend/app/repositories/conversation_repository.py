from sqlalchemy import select
from app.models import Chatbot, Conversation, Message, MessageSource


def scoped_conversations(organization_id, chatbot_id):
    return select(Conversation).join(Chatbot).where(Chatbot.organization_id == organization_id, Chatbot.id == chatbot_id)


def list_conversations(db, organization_id, chatbot_id):
    return list(db.scalars(scoped_conversations(organization_id, chatbot_id).order_by(Conversation.updated_at.desc(), Conversation.id)))


def get_conversation(db, organization_id, chatbot_id, conversation_id):
    return db.scalar(scoped_conversations(organization_id, chatbot_id).where(Conversation.id == conversation_id))


def messages(db, conversation_id):
    return list(db.scalars(select(Message).where(Message.conversation_id == conversation_id).order_by(Message.sequence)))


def recent_messages(db, conversation_id):
    return list(reversed(list(db.scalars(select(Message).where(Message.conversation_id == conversation_id)
                                        .order_by(Message.sequence.desc()).limit(8)))))


def sources(db, message_ids):
    return list(db.scalars(select(MessageSource).where(MessageSource.message_id.in_(message_ids)).order_by(MessageSource.score.desc()))) if message_ids else []
