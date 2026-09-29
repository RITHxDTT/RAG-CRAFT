from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field


class Question(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    question: str = Field(min_length=1, max_length=2000)
    conversation_id: UUID | None = None


class Source(BaseModel):
    chunk_id: UUID | None
    document_id: UUID | None
    document_name: str
    sheet_name: str | None = None
    row_number: int | None = None
    page_number: int | None
    chunk_index: int | None
    excerpt: str
    score: float


class GeneratedAnswer(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    answer: str = Field(min_length=1, max_length=12000)
    has_answer: bool
    source_ids: list[int] = Field(default_factory=list, max_length=20)


class RagAnswer(BaseModel):
    answer: str
    sources: list[Source]
