from uuid import UUID
from pydantic import BaseModel, EmailStr, Field, field_validator, ConfigDict, model_validator


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
    full_name: str = ""


class Registration(LoginInput):
    password: str = Field(min_length=12, max_length=256)
    model_config = ConfigDict(extra="forbid")
    full_name: str = Field(min_length=1, max_length=120)
    confirm_password: str = Field(min_length=12, max_length=256)

    @field_validator("full_name")
    @classmethod
    def name_not_blank(cls, value):
        if not value.strip():
            raise ValueError("Full name cannot be blank.")
        return value.strip()

    @model_validator(mode="after")
    def passwords_match(self):
        if self.password != self.confirm_password:
            raise ValueError("Passwords do not match.")
        return self


class ProfileUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    full_name: str = Field(min_length=1, max_length=120)


class ForgotPassword(BaseModel):
    email: EmailStr


class ResetPassword(BaseModel):
    token: str = Field(min_length=20, max_length=200)
    password: str = Field(min_length=12, max_length=256)
    confirm_password: str

    @model_validator(mode="after")
    def passwords_match(self):
        if self.password != self.confirm_password:
            raise ValueError("Passwords do not match.")
        return self
