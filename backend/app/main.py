import logging
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.middleware.body_limit import RequestBodyLimitMiddleware
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from app.api import auth, health, chatbots, knowledge, playground
from app.core.config import get_settings
from app.core.exceptions import AppError

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger(__name__)
app = FastAPI(title="RAG Craft API", version="2.0.0")
app.add_middleware(RequestBodyLimitMiddleware, max_body_size=get_settings().max_upload_size_mb * 1024 * 1024 + 65536)
app.add_middleware(CORSMiddleware, allow_origins=get_settings().cors_origins,
                   allow_credentials=True, allow_methods=["GET", "POST", "PATCH", "DELETE"],
                   allow_headers=["Content-Type", "Authorization", "X-Requested-With"])


@app.exception_handler(AppError)
async def application_error(request: Request, error: AppError):
    return JSONResponse(status_code=error.status_code, content={"detail": error.detail})


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, error: RequestValidationError):
    # Never echo submitted passwords or other request input in error responses.
    issues = [{"field": ".".join(str(part) for part in item["loc"]), "message": item["msg"]}
              for item in error.errors()]
    return JSONResponse(status_code=422, content={"detail": "Please check the submitted fields.", "issues": issues})


@app.exception_handler(IntegrityError)
async def conflict_error(request: Request, error: IntegrityError):
    return JSONResponse(status_code=409, content={"detail": "This record conflicts with existing data. Please refresh and retry."})


@app.exception_handler(SQLAlchemyError)
async def database_error(request: Request, error: SQLAlchemyError):
    logger.error("Database operation failed: %s", type(error).__name__)
    return JSONResponse(status_code=503, content={"detail": "The database is temporarily unavailable. Please try again."})


@app.exception_handler(Exception)
async def unexpected_error(request: Request, error: Exception):
    logger.error("Request failed: %s", type(error).__name__)
    return JSONResponse(status_code=500, content={"detail": "An unexpected error occurred. Please try again."})


app.include_router(health.router)
app.include_router(auth.router)

app.include_router(chatbots.router)

app.include_router(knowledge.router)

app.include_router(playground.router)

from app.api import catalog
app.include_router(catalog.router)

from app.api import channels
app.include_router(channels.router)
app.include_router(channels.public_router)

from app.api import telegram
app.include_router(telegram.router)

from app.api import admin
app.include_router(admin.router)
