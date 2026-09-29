from uuid import UUID
from sqlalchemy import Boolean, CheckConstraint, ForeignKey, String, Text, UniqueConstraint, BigInteger
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base, Record


class ChannelIntegration(Record, Base):
    __tablename__='channel_integrations'
    __table_args__=(UniqueConstraint('chatbot_id','channel'), CheckConstraint("channel IN ('PUBLIC_LINK','WEB_WIDGET','TELEGRAM')",name='channel'))
    chatbot_id: Mapped[UUID]=mapped_column(ForeignKey('chatbots.id',ondelete='CASCADE'),index=True)
    channel: Mapped[str]=mapped_column(String(20))
    public_id: Mapped[str]=mapped_column(String(64),unique=True)
    enabled: Mapped[bool]=mapped_column(Boolean,default=True)
    status: Mapped[str]=mapped_column(String(20),default='CONNECTED')
    credential: Mapped[str | None]=mapped_column(Text)
    webhook_secret: Mapped[str | None]=mapped_column(String(64))
    telegram_bot_id: Mapped[str | None]=mapped_column(String(40),unique=True)
    telegram_username: Mapped[str | None]=mapped_column(String(120))


class TelegramUpdate(Record, Base):
    __tablename__='telegram_updates'
    __table_args__=(UniqueConstraint('integration_id','update_id'),)
    integration_id: Mapped[UUID]=mapped_column(ForeignKey('channel_integrations.id',ondelete='CASCADE'),index=True)
    update_id: Mapped[int]=mapped_column(BigInteger)
    answer: Mapped[str]=mapped_column(Text)
    sent_parts: Mapped[int]=mapped_column(default=0)
