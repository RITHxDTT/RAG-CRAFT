import logging
from uuid import uuid4
from sqlalchemy import delete, select, update
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.models import Chatbot, Document, DocumentChunk, IngestionJob, KnowledgeSource
from app.rag import embedding_service
from app.rag.chunker import chunk_pages
from app.rag.loaders import extract
from app.rag.vector_store import VectorStore
from app.services.file_storage import storage_path

logger = logging.getLogger(__name__)


def recover_interrupted(db):
    # Called only while holding the exclusive worker advisory lock.
    db.execute(update(Document).where(Document.status == "PROCESSING").values(status="QUEUED", error_message=None))
    db.execute(update(IngestionJob).where(IngestionJob.status == "PROCESSING").values(status="QUEUED", error_message=None))
    db.commit()


def claim_next(db):
    row = db.execute(select(IngestionJob, Document, KnowledgeSource, Chatbot)
                     .join(Document, IngestionJob.document_id == Document.id)
                     .join(KnowledgeSource, Document.knowledge_source_id == KnowledgeSource.id)
                     .join(Chatbot, KnowledgeSource.chatbot_id == Chatbot.id)
                     .where(IngestionJob.status == "QUEUED", Document.status == "QUEUED")
                     .order_by(IngestionJob.created_at, IngestionJob.id).limit(1)).first()
    if not row:
        db.rollback()
        return None
    job, doc, source, bot = row
    # Match the lock order of upload, retry, and delete APIs.
    if not db.scalar(select(Chatbot).where(Chatbot.id == bot.id).with_for_update()):
        db.rollback()
        return None
    db.refresh(doc)
    db.refresh(job)
    if doc.status != "QUEUED" or job.status != "QUEUED":
        db.rollback()
        return None
    job.status = doc.status = "PROCESSING"
    job.error_message = doc.error_message = None
    snapshot = {"job_id": job.id, "document_id": doc.id, "chatbot_id": bot.id,
                "organization_id": bot.organization_id, "name": doc.name,
                "storage_key": doc.storage_key, "file_type": doc.file_type}
    db.commit()
    return snapshot


def process_claimed(db, item, store=None):
    store = store or VectorStore()
    config = get_settings()
    doc_id = item["document_id"]
    logger.info("Ingestion started document_id=%s", doc_id)
    try:
        pages = extract(storage_path(item["storage_key"]), item["file_type"])
        logger.info("Text extraction completed document_id=%s pages=%s", doc_id, len(pages))
        chunks = chunk_pages(pages)
        logger.info("Chunking completed document_id=%s chunks=%s", doc_id, len(chunks))
        vectors = []
        logger.info("Embedding started document_id=%s", doc_id)
        for start in range(0, len(chunks), config.embedding_batch_size):
            vectors.extend(embedding_service.embed([c.content for c in chunks[start:start + config.embedding_batch_size]]))
        store.ensure_collection(len(vectors[0]))
        records, points = [], []
        for chunk, vector in zip(chunks, vectors, strict=True):
            id = uuid4()
            records.append(DocumentChunk(id=id, document_id=doc_id, chunk_index=chunk.chunk_index,
                                         page_number=chunk.page_number, content=chunk.content))
            points.append({"id": str(id), "vector": vector, "payload": {
                "organization_id": str(item["organization_id"]), "chatbot_id": str(item["chatbot_id"]),
                "document_id": str(doc_id), "chunk_id": str(id), "document_name": item["name"],
                "page_number": chunk.page_number, "chunk_index": chunk.chunk_index,
                "source_type": "DOCUMENT", "embedding_model": config.embedding_model,
            }})
        store.delete(item["organization_id"], item["chatbot_id"], doc_id)
        store.upsert(points)
        db.execute(delete(DocumentChunk).where(DocumentChunk.document_id == doc_id))
        db.add_all(records)
        db.get(Document, doc_id).status = "READY"
        db.get(IngestionJob, item["job_id"]).status = "READY"
        db.commit()
        logger.info("Indexing completed document_id=%s chunks=%s", doc_id, len(chunks))
    except Exception as error:
        db.rollback()
        message = error.detail if isinstance(error, AppError) else "Document processing failed. Retry or upload a readable document."
        doc, job = db.get(Document, doc_id), db.get(IngestionJob, item["job_id"])
        if doc and job:
            doc.status = job.status = "FAILED"
            doc.error_message = job.error_message = message
            db.commit()
        logger.error("Ingestion failed document_id=%s error_type=%s", doc_id, type(error).__name__)
        # Partial vectors cannot be retrieved: PostgreSQL never marked the document READY.
        return False
    return True
