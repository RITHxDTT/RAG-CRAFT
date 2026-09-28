from sqlalchemy import func, select
from sqlalchemy.orm import Session
from app.models import Chatbot, Document, KnowledgeSource


def list_chatbots(db: Session, organization_id):
    return list(db.scalars(select(Chatbot).where(Chatbot.organization_id == organization_id)
                          .order_by(Chatbot.created_at.desc(), Chatbot.id)))


def get_chatbot(db: Session, organization_id, chatbot_id, *, lock=False):
    query = select(Chatbot).where(Chatbot.id == chatbot_id, Chatbot.organization_id == organization_id)
    if lock:
        query = query.with_for_update()
    return db.scalar(query)


def document_counts(db: Session, organization_id):
    return db.execute(select(KnowledgeSource.chatbot_id, Document.status, func.count(Document.id))
                      .join(Document, Document.knowledge_source_id == KnowledgeSource.id)
                      .join(Chatbot, Chatbot.id == KnowledgeSource.chatbot_id)
                      .where(Chatbot.organization_id == organization_id)
                      .group_by(KnowledgeSource.chatbot_id, Document.status)).all()
