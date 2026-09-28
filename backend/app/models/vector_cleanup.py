from datetime import datetime
from uuid import UUID
from sqlalchemy import DateTime, Integer, Text, func
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base, Record


class VectorCleanup(Record, Base):
    __tablename__ = "vector_cleanup_jobs"
    # Deliberately no foreign keys: the task must survive deletion of its owner/resources.
    organization_id: Mapped[UUID]
    chatbot_id: Mapped[UUID]
    document_id: Mapped[UUID | None]
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    error_message: Mapped[str | None] = mapped_column(Text)
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)
