from functools import lru_cache
from pathlib import Path
from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND = Path(__file__).resolve().parents[2]
PROJECT = BACKEND.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=(PROJECT / ".env", BACKEND / ".env"), extra="ignore")
    database_url: str
    jwt_secret: SecretStr = Field(min_length=32)
    session_minutes: int = Field(default=480, ge=1, le=10080)
    cookie_secure: bool = False
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "rag_craft"
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "llama3.2:3b"
    embedding_model: str = "nomic-embed-text"
    top_k: int = Field(default=5, ge=1, le=20)
    max_upload_size_mb: int = Field(default=20, ge=1, le=100)
    storage_dir: Path = BACKEND / "storage"
    cors_origins: list[str] = ["http://localhost:3000"]


@lru_cache
def get_settings() -> Settings:
    return Settings()
