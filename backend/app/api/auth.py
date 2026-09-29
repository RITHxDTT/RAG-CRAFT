from fastapi import APIRouter, Depends, Response, Request
from app.api.dependencies import Authenticated, DB, require_csrf
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
def me(admin: Authenticated, response: Response):
    response.headers["Cache-Control"] = "no-store"
    return auth_service.profile(admin)


@router.post("/logout", status_code=204)
def logout(admin: Authenticated, db: DB, response: Response):
    auth_service.logout(db, admin)
    response.delete_cookie(COOKIE_NAME, path="/", secure=get_settings().cookie_secure,
                           httponly=True, samesite="lax")


from app.schemas.auth import Registration, ProfileUpdate, ForgotPassword, ResetPassword
from app.services import password_service


@router.post("/register", response_model=CurrentUser, status_code=201)
def register(data: Registration, response: Response, request: Request, db: DB):
    login_limiter.check(request.client.host if request.client else "unknown")
    user, token = auth_service.register(db, data)
    config = get_settings()
    response.set_cookie(COOKIE_NAME, token, httponly=True, secure=config.cookie_secure,
                        samesite="lax", max_age=config.session_minutes * 60, path="/")
    response.headers["Cache-Control"] = "no-store"
    return user


@router.patch("/me", response_model=CurrentUser)
def update_profile(data: ProfileUpdate, user: Authenticated, db: DB):
    return auth_service.update_profile(db, user, data)


@router.post("/forgot-password")
def forgot(data: ForgotPassword, request: Request, db: DB):
    login_limiter.check(request.client.host if request.client else "unknown")
    password_service.forgot(db, str(data.email).lower())
    return {"message": "If that account exists, a reset email has been sent."}


@router.post("/reset-password", status_code=204)
def reset(data: ResetPassword, request: Request, db: DB):
    login_limiter.check(request.client.host if request.client else "unknown")
    password_service.reset(db, data)
