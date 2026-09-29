from typing import Annotated
from fastapi import Depends, Request
from sqlalchemy.orm import Session
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.core.security import COOKIE_NAME
from app.db.session import get_db
from app.services.auth_service import AdminContext, authenticate

DB = Annotated[Session, Depends(get_db)]


def require_csrf(request: Request):
    if request.method in {"GET", "HEAD", "OPTIONS"}:
        return
    # A custom header requires browser preflight; disallowed origins cannot send it.
    if request.headers.get("X-Requested-With") != "RAGCraft":
        raise AppError(403, "Missing request protection header.")
    origin = request.headers.get("origin")
    if origin and origin not in get_settings().cors_origins:
        raise AppError(403, "This origin is not allowed.")


def current_admin(request: Request, db: DB) -> AdminContext:
    ctx = authenticate(db, request.cookies.get(COOKIE_NAME))
    if ctx.user.role != "ADMIN":
        raise AppError(403, "Administrator access is required.")
    return ctx


Admin = Annotated[AdminContext, Depends(current_admin)]


def current_user(request: Request, db: DB) -> AdminContext:
    return authenticate(db, request.cookies.get(COOKIE_NAME))

Authenticated = Annotated[AdminContext, Depends(current_user)]
