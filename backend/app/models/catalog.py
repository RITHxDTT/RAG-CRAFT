from sqlalchemy import Boolean, CheckConstraint, String, Text, Index, text
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base, Record


class PlatformModel(Record, Base):
    __tablename__ = 'models'
    __table_args__ = (CheckConstraint("provider = 'OLLAMA'", name='provider'),
        CheckConstraint('NOT is_default OR enabled', name='default_enabled'),
        Index('uq_models_default', 'is_default', unique=True, postgresql_where=text('is_default'), sqlite_where=text('is_default')))
    name: Mapped[str] = mapped_column(String(120))
    provider: Mapped[str] = mapped_column(String(20), default='OLLAMA')
    model_identifier: Mapped[str] = mapped_column(String(120), unique=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    is_default: Mapped[bool] = mapped_column(Boolean, default=False)


class PromptTemplate(Record, Base):
    __tablename__ = 'system_prompt_templates'
    name: Mapped[str] = mapped_column(String(120))
    prompt: Mapped[str] = mapped_column(Text)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
