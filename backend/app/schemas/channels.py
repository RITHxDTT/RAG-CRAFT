from typing import Literal
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field
from app.schemas.rag import Question

Channel = Literal['PUBLIC_LINK','WEB_WIDGET','TELEGRAM']

class ChannelCreate(BaseModel):
    model_config=ConfigDict(extra='forbid')
    channel: Channel
    token: str | None = Field(default=None, max_length=200, pattern=r'^\d+:[A-Za-z0-9_-]+$')

class ChannelUpdate(BaseModel):
    model_config=ConfigDict(extra='forbid')
    enabled: bool

class PublicQuestion(Question):
    session_token: str | None = Field(default=None,min_length=20,max_length=100)

class ChannelView(BaseModel):
    id: UUID
    channel: Channel
    public_id: str
    enabled: bool
    status: str
    url: str | None = None
    embed_code: str | None = None
    telegram_username: str | None = None
    token_mask: str | None = None
