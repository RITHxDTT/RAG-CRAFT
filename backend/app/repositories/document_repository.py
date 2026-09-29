from sqlalchemy import select
from sqlalchemy.orm import Session
from app.models import Chatbot, Document, IngestionJob, KnowledgeSource


def scoped_documents(organization_id, chatbot_id):
    return (select(Document).join(KnowledgeSource, Document.knowledge_source_id == KnowledgeSource.id)
            .join(Chatbot, KnowledgeSource.chatbot_id == Chatbot.id)
            .where(Chatbot.organization_id == organization_id, Chatbot.id == chatbot_id))


def list_documents(db: Session, organization_id, chatbot_id):
    return list(db.scalars(scoped_documents(organization_id, chatbot_id).order_by(Document.created_at.desc(), Document.id)))


def get_document(db: Session, organization_id, chatbot_id, document_id):
    return db.scalar(scoped_documents(organization_id, chatbot_id).where(Document.id == document_id))


def duplicate_source(db: Session, chatbot_id, checksum):
    return db.scalar(select(KnowledgeSource).where(KnowledgeSource.chatbot_id == chatbot_id,
                                                  KnowledgeSource.source_type == "DOCUMENT",
                                                  KnowledgeSource.source_key == checksum))


def jobs_for_document(db: Session, document_id):
    return list(db.scalars(select(IngestionJob).where(IngestionJob.document_id == document_id)
                           .order_by(IngestionJob.created_at.desc(), IngestionJob.id)))


def recent_activity(db: Session, organization_id, owner_id):
    return db.execute(select(Document, Chatbot.id, Chatbot.name)
                      .join(KnowledgeSource, Document.knowledge_source_id == KnowledgeSource.id)
                      .join(Chatbot, KnowledgeSource.chatbot_id == Chatbot.id)
                      .where(Chatbot.organization_id == organization_id, Chatbot.owner_id == owner_id)
                      .order_by(Document.updated_at.desc(), Document.id).limit(8)).all()
