from uuid import UUID
from sqlalchemy import CheckConstraint, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.db.base import Base, Record


class Chatbot(Record, Base):
    __tablename__ = "chatbots"
    __table_args__ = (CheckConstraint("status IN ('ACTIVE', 'INACTIVE')", name="valid_status"),)
    organization_id: Mapped[UUID] = mapped_column(ForeignKey("organizations.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(20), default="ACTIVE")
    settings: Mapped["ChatbotSetting"] = relationship(cascade="all, delete-orphan", uselist=False, lazy="selectin")


class ChatbotSetting(Record, Base):
    __tablename__ = "chatbot_settings"
    __table_args__ = (
        CheckConstraint("temperature >= 0 AND temperature <= 2", name="temperature_range"),
        CheckConstraint("top_k >= 1 AND top_k <= 20", name="top_k_range"),
        CheckConstraint("answer_length IN ('SHORT', 'MEDIUM', 'LONG')", name="valid_answer_length"),
    )
    chatbot_id: Mapped[UUID] = mapped_column(ForeignKey("chatbots.id", ondelete="CASCADE"), unique=True)
    system_instruction: Mapped[str] = mapped_column(Text)
    model_name: Mapped[str] = mapped_column(String(120))
    temperature: Mapped[float] = mapped_column(Float, default=0.2)
    answer_length: Mapped[str] = mapped_column(String(20), default="MEDIUM")
    top_k: Mapped[int] = mapped_column(Integer, default=5)
