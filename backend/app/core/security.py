from datetime import datetime, timedelta, timezone
from uuid import UUID
import jwt
from pwdlib import PasswordHash
from app.core.config import get_settings
from app.core.exceptions import AppError

password_hasher = PasswordHash.recommended()
DUMMY_HASH = password_hasher.hash("not-an-account-password")
COOKIE_NAME = "ragcraft_session"


def hash_password(password: str) -> str:
    return password_hasher.hash(password)


def verify_password(password: str, hashed: str) -> bool:
    return password_hasher.verify(password, hashed)


def create_token(user_id: UUID, organization_id: UUID, version: int) -> str:
    settings = get_settings()
    now = datetime.now(timezone.utc)
    return jwt.encode({"sub": str(user_id), "org": str(organization_id), "ver": version,
                       "iat": now, "exp": now + timedelta(minutes=settings.session_minutes),
                       "iss": "rag-craft", "aud": "rag-craft-admin"},
                      settings.jwt_secret.get_secret_value(), algorithm="HS256")


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, get_settings().jwt_secret.get_secret_value(), algorithms=["HS256"],
                          issuer="rag-craft", audience="rag-craft-admin",
                          options={"require": ["sub", "org", "ver", "exp", "iat"]})
    except jwt.InvalidTokenError:
        raise AppError(401, "Please log in again.") from None
