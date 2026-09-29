from datetime import datetime
from typing import Literal, Annotated
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field, model_validator
from app.core.config import get_settings

DEFAULT_INSTRUCTION = "Answer questions using the provided knowledge. If the answer cannot be found, clearly tell the user that the available knowledge does not contain enough information."


class BotSettings(BaseModel):
    model_config = ConfigDict(from_attributes=True, str_strip_whitespace=True, extra="forbid")
    model_id: UUID | None = None
    prompt_template_id: UUID | None = None
    tone: Literal["PROFESSIONAL", "FRIENDLY", "CONCISE", "EDUCATIONAL"] = "PROFESSIONAL"
    custom_instruction: str = Field(default="", max_length=10000)
    system_instruction: str = Field(default=DEFAULT_INSTRUCTION, min_length=1, max_length=10000)
    model_name: str = Field(default_factory=lambda: get_settings().ollama_model, min_length=1, max_length=120)
    temperature: float = Field(default=0.2, ge=0, le=2, allow_inf_nan=False)
    answer_length: Literal["SHORT", "MEDIUM", "LONG"] = "MEDIUM"
    top_k: int = Field(default_factory=lambda: get_settings().top_k, ge=1, le=20)


class ChatbotCreate(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    name: str = Field(min_length=1, max_length=120)
    starter_questions: list[Annotated[str, Field(min_length=1, max_length=300)]] = Field(default_factory=list, max_length=10)
    description: str = Field(default="", max_length=2000)
    status: Literal["ACTIVE", "INACTIVE"] = "ACTIVE"
    settings: BotSettings = Field(default_factory=BotSettings)


class ChatbotUpdate(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    name: str | None = Field(default=None, min_length=1, max_length=120)
    starter_questions: list[Annotated[str, Field(min_length=1, max_length=300)]] | None = Field(default=None, max_length=10)
    description: str | None = Field(default=None, max_length=2000)
    status: Literal["ACTIVE", "INACTIVE"] | None = None
    settings: BotSettings | None = None

    @model_validator(mode="after")
    def reject_nulls(self):
        if any(getattr(self, field) is None for field in self.model_fields_set):
            raise ValueError("Supplied fields cannot be null.")
        return self


class ChatbotView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    starter_questions: list[str] = Field(default_factory=list)
    organization_id: UUID
    name: str
    description: str
    status: str
    created_at: datetime
    updated_at: datetime
    settings: BotSettings
    document_count: int = 0
    ready_count: int = 0
    failed_count: int = 0


class Dashboard(BaseModel):
    total_chatbots: int
    total_documents: int
    ready_documents: int
    failed_documents: int
    recent_chatbots: list[ChatbotView]
