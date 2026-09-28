from datetime import datetime
from typing import Literal
from uuid import UUID
from pydantic import BaseModel, ConfigDict


class DocumentView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    name: str
    file_type: str
    mime_type: str
    size_bytes: int
    status: Literal["QUEUED", "PROCESSING", "READY", "FAILED"]
    error_message: str | None
    created_at: datetime
    updated_at: datetime


class JobView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    status: str
    error_message: str | None
    created_at: datetime
    updated_at: datetime


class DocumentDetail(DocumentView):
    jobs: list[JobView]


class UploadPolicy(BaseModel):
    max_upload_size_mb: int
    extensions: list[str]
    processing_enabled: bool = False


class DocumentActivity(DocumentView):
    chatbot_id: UUID
    chatbot_name: str
