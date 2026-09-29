from uuid import UUID
from fastapi import APIRouter, Depends, Request
from fastapi.responses import FileResponse
from starlette.concurrency import run_in_threadpool
from starlette.datastructures import UploadFile
from app.api.dependencies import Authenticated, DB, require_csrf
from app.core.exceptions import AppError
from app.schemas.document import DocumentActivity, DocumentDetail, DocumentView, UploadPolicy
from app.services import document_service

router = APIRouter(prefix="/api", tags=["knowledge"], dependencies=[Depends(require_csrf)])


@router.get("/knowledge/config", response_model=UploadPolicy)
def policy(admin: Authenticated, db: DB):
    return document_service.upload_policy(db)


@router.get("/knowledge/recent", response_model=list[DocumentActivity])
def recent(db: DB, admin: Authenticated):
    return document_service.recent_activity(db, admin)


@router.get("/chatbots/{chatbot_id}/documents", response_model=list[DocumentView])
def list_documents(chatbot_id: UUID, db: DB, admin: Authenticated):
    return document_service.list_documents(db, admin, chatbot_id)


@router.post("/chatbots/{chatbot_id}/documents", response_model=DocumentView, status_code=201,
             openapi_extra={"requestBody": {"required": True, "content": {"multipart/form-data": {
                 "schema": {"type": "object", "required": ["file"], "properties": {"file": {"type": "string", "format": "binary"}}}}}}})
async def upload(chatbot_id: UUID, request: Request, db: DB, admin: Authenticated):
    # Parse only after authentication; bound the entire body in middleware as well.
    async with request.form(max_files=1, max_fields=0) as form:
        file = form.get("file")
        if not isinstance(file, UploadFile):
            raise AppError(422, "Choose one document in the file field.")
        return await run_in_threadpool(document_service.upload, db, admin, chatbot_id, file)


@router.get("/chatbots/{chatbot_id}/documents/{document_id}", response_model=DocumentDetail)
def detail(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    return document_service.detail(db, admin, chatbot_id, document_id)


@router.get("/chatbots/{chatbot_id}/documents/{document_id}/download")
def download(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    path, name = document_service.download(db, admin, chatbot_id, document_id)
    return FileResponse(path, media_type="application/octet-stream", filename=name,
                        headers={"X-Content-Type-Options": "nosniff", "Cache-Control": "no-store"})


@router.delete("/chatbots/{chatbot_id}/documents/{document_id}", status_code=204)
def delete(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    document_service.delete_document(db, admin, chatbot_id, document_id)


@router.post("/chatbots/{chatbot_id}/documents/{document_id}/retry", response_model=DocumentView)
def retry(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    return document_service.requeue(db, admin, chatbot_id, document_id, retry=True)


@router.post("/chatbots/{chatbot_id}/documents/{document_id}/reindex", response_model=DocumentView)
def reindex(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    return document_service.requeue(db, admin, chatbot_id, document_id, retry=False)


@router.get("/chatbots/{chatbot_id}/documents/{document_id}/preview")
def preview(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    doc = document_service.require_document(db, admin, chatbot_id, document_id)
    if doc.file_type != "PDF":
        raise AppError(422, "Inline preview is available for PDFs only.")
    path, name = document_service.download(db, admin, chatbot_id, document_id)
    return FileResponse(path, media_type="application/pdf", filename=name, content_disposition_type="inline",
                        headers={"X-Content-Type-Options": "nosniff", "Cache-Control": "no-store"})


@router.get("/chatbots/{chatbot_id}/documents/{document_id}/availability")
def availability(chatbot_id: UUID, document_id: UUID, db: DB, admin: Authenticated):
    document_service.download(db, admin, chatbot_id, document_id)
    return {"available": True}
