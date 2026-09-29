from datetime import datetime
from uuid import UUID
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field

class ModelInput(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)
    name: str = Field(min_length=1, max_length=120)
    provider: Literal['OLLAMA'] = 'OLLAMA'
    model_identifier: str = Field(min_length=1, max_length=120, pattern=r'^[a-zA-Z0-9][a-zA-Z0-9_.:/-]*$')
    enabled: bool = True
    is_default: bool = False

class ModelView(ModelInput):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    created_at: datetime
    updated_at: datetime

class PromptInput(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)
    name: str = Field(min_length=1, max_length=120)
    prompt: str = Field(min_length=1, max_length=10000)
    enabled: bool = True

class PromptView(PromptInput):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    created_at: datetime
    updated_at: datetime
