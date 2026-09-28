from fastapi import APIRouter, Depends, Response, Request
from app.api.dependencies import Admin, DB, require_csrf
from app.core.config import get_settings
from app.core.security import COOKIE_NAME
from app.core.rate_limit import login_limiter
from app.schemas.auth import CurrentUser, LoginInput
from app.services import auth_service

router = APIRouter(prefix="/api/auth", tags=["auth"], dependencies=[Depends(require_csrf)])


@router.post("/login", response_model=CurrentUser)
def login(data: LoginInput, response: Response, request: Request, db: DB):
    login_limiter.check(request.client.host if request.client else "unknown")
    user, token = auth_service.login(db, data)
    config = get_settings()
    response.set_cookie(COOKIE_NAME, token, httponly=True, secure=config.cookie_secure,
                        samesite="lax", max_age=config.session_minutes * 60, path="/")
    response.headers["Cache-Control"] = "no-store"
    return user


@router.get("/me", response_model=CurrentUser)
def me(admin: Admin, response: Response):
    response.headers["Cache-Control"] = "no-store"
    return auth_service.profile(admin)


@router.post("/logout", status_code=204)
def logout(admin: Admin, db: DB, response: Response):
    auth_service.logout(db, admin)
    response.delete_cookie(COOKIE_NAME, path="/", secure=get_settings().cookie_secure,
                           httponly=True, samesite="lax")
