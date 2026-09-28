from uuid import UUID
from sqlalchemy import CheckConstraint, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base, Record


class KnowledgeSource(Record, Base):
    __tablename__ = "knowledge_sources"
    chatbot_id: Mapped[UUID] = mapped_column(ForeignKey("chatbots.id", ondelete="CASCADE"), index=True)
    source_type: Mapped[str] = mapped_column(String(30), default="DOCUMENT")
    # A checksum is a document source's external key; future sources can use a URL or provider ID.
    source_key: Mapped[str] = mapped_column(String(512))
    __table_args__ = (UniqueConstraint("chatbot_id", "source_type", "source_key"),)


class Document(Record, Base):
    __tablename__ = "documents"
    __table_args__ = (
        CheckConstraint("status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED')", name="valid_status"),
        CheckConstraint("size_bytes > 0", name="positive_size"),
    )
    knowledge_source_id: Mapped[UUID] = mapped_column(ForeignKey("knowledge_sources.id", ondelete="CASCADE"), unique=True)
    name: Mapped[str] = mapped_column(String(255))
    file_type: Mapped[str] = mapped_column(String(10))
    mime_type: Mapped[str] = mapped_column(String(120))
    size_bytes: Mapped[int] = mapped_column(Integer)
    storage_key: Mapped[str] = mapped_column(String(80), unique=True)
    status: Mapped[str] = mapped_column(String(20), default="QUEUED", index=True)
    error_message: Mapped[str | None] = mapped_column(Text)


class DocumentChunk(Record, Base):
    __tablename__ = "document_chunks"
    __table_args__ = (UniqueConstraint("document_id", "chunk_index"),)
    document_id: Mapped[UUID] = mapped_column(ForeignKey("documents.id", ondelete="CASCADE"), index=True)
    chunk_index: Mapped[int] = mapped_column(Integer)
    page_number: Mapped[int | None] = mapped_column(Integer)
    content: Mapped[str] = mapped_column(Text)


class IngestionJob(Record, Base):
    __tablename__ = "ingestion_jobs"
    __table_args__ = (CheckConstraint("status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED')", name="valid_status"),)
    document_id: Mapped[UUID] = mapped_column(ForeignKey("documents.id", ondelete="CASCADE"), index=True)
    status: Mapped[str] = mapped_column(String(20), default="QUEUED", index=True)
    error_message: Mapped[str | None] = mapped_column(Text)
