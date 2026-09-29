from app.services.vector_cleanup_service import schedule_cleanup
import logging
from uuid import uuid4
from sqlalchemy.orm import Session
from starlette.datastructures import UploadFile
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.models import AuditLog, Document, IngestionJob, KnowledgeSource
from app.repositories import document_repository as documents
from app.schemas.document import DocumentActivity, DocumentDetail, DocumentView, JobView, UploadPolicy
from app.services.auth_service import AdminContext
from app.services.chatbot_service import require_chatbot
from app.services.file_storage import save_file, staged_deletion, storage_path
from app.services.file_validation import MIME_TYPES, validate_file

logger = logging.getLogger(__name__)


def upload_policy(db=None) -> UploadPolicy:
    from app.core.worker_lock import worker_is_running
    return UploadPolicy(max_upload_size_mb=get_settings().max_upload_size_mb, extensions=list(MIME_TYPES), processing_enabled=True, worker_running=worker_is_running(db) if db is not None else False)


def audit(db, ctx, action, document_id):
    db.add(AuditLog(user_id=ctx.user.id, organization_id=ctx.organization_id, action=action,
                    resource_type="document", resource_id=document_id))


def list_documents(db: Session, ctx: AdminContext, chatbot_id):
    require_chatbot(db, ctx, chatbot_id)
    return documents.list_documents(db, ctx.organization_id, chatbot_id)


def require_document(db, ctx, chatbot_id, document_id):
    require_chatbot(db, ctx, chatbot_id)
    doc = documents.get_document(db, ctx.organization_id, chatbot_id, document_id)
    if not doc:
        raise AppError(404, "Document not found.")
    return doc


def detail(db, ctx, chatbot_id, document_id):
    doc = require_document(db, ctx, chatbot_id, document_id)
    return DocumentDetail(**DocumentView.model_validate(doc).model_dump(),
                          jobs=[JobView.model_validate(job) for job in documents.jobs_for_document(db, doc.id)])


def upload(db: Session, ctx: AdminContext, chatbot_id, file: UploadFile):
    # Lock the chatbot to serialize upload/delete/requeue operations for this assistant.
    require_chatbot(db, ctx, chatbot_id, lock=True)
    max_bytes = get_settings().max_upload_size_mb * 1024 * 1024
    content = file.file.read(max_bytes + 1)
    validated = validate_file(file.filename, file.content_type, content, max_bytes)
    if documents.duplicate_source(db, chatbot_id, validated.checksum):
        raise AppError(409, "This document has already been uploaded to this chatbot.")
    source = KnowledgeSource(chatbot_id=chatbot_id, source_type="DOCUMENT", source_key=validated.checksum)
    db.add(source)
    db.flush()
    key = f"{uuid4().hex}.{validated.file_type.lower()}"
    path = save_file(key, content)
    try:
        doc = Document(knowledge_source_id=source.id, name=validated.name, file_type=validated.file_type,
                       mime_type=validated.mime_type, size_bytes=len(content), storage_key=key, status="QUEUED")
        db.add(doc)
        db.flush()
        db.add(IngestionJob(document_id=doc.id, status="QUEUED"))
        audit(db, ctx, "document.uploaded", doc.id)
        db.commit()
    except Exception:
        db.rollback()
        path.unlink(missing_ok=True)
        raise
    logger.info("Document uploaded document_id=%s chatbot_id=%s status=QUEUED", doc.id, chatbot_id)
    return doc


def download(db, ctx, chatbot_id, document_id):
    doc = require_document(db, ctx, chatbot_id, document_id)
    path = storage_path(doc.storage_key)
    if not path.is_file():
        raise AppError(404, "The stored file is unavailable. Delete this record and upload the file again.")
    return path, doc.name


def delete_document(db, ctx, chatbot_id, document_id):
    require_chatbot(db, ctx, chatbot_id, lock=True)
    doc = require_document(db, ctx, chatbot_id, document_id)
    if doc.status == "PROCESSING":
        raise AppError(409, "Wait for processing to finish before deleting this document.")
    source = db.get(KnowledgeSource, doc.knowledge_source_id)
    with staged_deletion([doc.storage_key]):
        audit(db, ctx, "document.deleted", doc.id)
        schedule_cleanup(db, ctx.organization_id, chatbot_id, doc.id)
        db.delete(source)
        db.commit()


def requeue(db, ctx, chatbot_id, document_id, *, retry: bool):
    require_chatbot(db, ctx, chatbot_id, lock=True)
    doc = require_document(db, ctx, chatbot_id, document_id)
    if doc.status not in ({"FAILED"} if retry else {"FAILED", "READY"}):
        raise AppError(409, "Retry requires a failed document. Re-index requires a ready or failed document.")
    if not storage_path(doc.storage_key).is_file():
        raise AppError(409, "The original file is missing. Upload it again.")
    doc.status = "QUEUED"
    doc.error_message = None
    db.add(IngestionJob(document_id=doc.id, status="QUEUED"))
    audit(db, ctx, "document.retry" if retry else "document.reindex", doc.id)
    db.commit()
    return doc


def recent_activity(db, ctx):
    return [DocumentActivity(**DocumentView.model_validate(doc).model_dump(), chatbot_id=bot_id, chatbot_name=bot_name)
            for doc, bot_id, bot_name in documents.recent_activity(db, ctx.organization_id, ctx.user.id)]
