from uuid import UUID
from pydantic import BaseModel, EmailStr, Field, field_validator


class LoginInput(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=256)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value):
        return str(value).lower()


class AdminCreate(LoginInput):
    password: str = Field(min_length=12, max_length=256)
    organization_name: str = Field(default="Default Organization", min_length=1, max_length=120)


class CurrentUser(BaseModel):
    id: UUID
    email: str
    organization_id: UUID
    organization_name: str
    role: str
