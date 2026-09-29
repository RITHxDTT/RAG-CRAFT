from uuid import UUID
from sqlalchemy import CheckConstraint, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base, Record


class Conversation(Record, Base):
    __tablename__ = "conversations"
    __table_args__ = (CheckConstraint("channel IN ('PLAYGROUND', 'TELEGRAM', 'WEB_WIDGET', 'PUBLIC_LINK')", name="channel"),)
    channel: Mapped[str] = mapped_column(String(20), default="PLAYGROUND", index=True)
    session_hash: Mapped[str | None] = mapped_column(String(64), index=True)
    integration_id: Mapped[UUID | None] = mapped_column(ForeignKey("channel_integrations.id", ondelete="SET NULL"), index=True)
    # Organization is derived through the chatbot, preventing contradictory ownership.
    chatbot_id: Mapped[UUID] = mapped_column(ForeignKey("chatbots.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(200), default="New conversation")


class Message(Record, Base):
    __tablename__ = "messages"
    __table_args__ = (CheckConstraint("role IN ('USER', 'ASSISTANT')", name="valid_role"),
                      UniqueConstraint("conversation_id", "sequence"),)
    conversation_id: Mapped[UUID] = mapped_column(ForeignKey("conversations.id", ondelete="CASCADE"), index=True)
    sequence: Mapped[int] = mapped_column(Integer)
    role: Mapped[str] = mapped_column(String(20))
    content: Mapped[str] = mapped_column(Text)


class MessageSource(Record, Base):
    __tablename__ = "message_sources"
    message_id: Mapped[UUID] = mapped_column(ForeignKey("messages.id", ondelete="CASCADE"), index=True)
    document_chunk_id: Mapped[UUID | None] = mapped_column(ForeignKey("document_chunks.id", ondelete="SET NULL"), index=True)
    # Historical citations survive document deletion/re-indexing.
    document_id: Mapped[UUID | None] = mapped_column(index=True)
    chunk_index: Mapped[int | None] = mapped_column(Integer)
    excerpt: Mapped[str | None] = mapped_column(Text)
    document_name: Mapped[str] = mapped_column(String(255))
    sheet_name: Mapped[str | None] = mapped_column(String(120))
    row_number: Mapped[int | None] = mapped_column(Integer)
    page_number: Mapped[int | None] = mapped_column(Integer)
    score: Mapped[float | None] = mapped_column(Float)
