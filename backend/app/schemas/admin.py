from datetime import datetime
from uuid import UUID
from pydantic import BaseModel,ConfigDict

class UserView(BaseModel):
    id:UUID
    full_name:str
    email:str
    role:str
    is_active:bool
    chatbot_count:int
    created_at:datetime

class UserStatus(BaseModel):
    model_config=ConfigDict(extra='forbid')
    is_active:bool
