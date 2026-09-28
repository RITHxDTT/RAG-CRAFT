from functools import lru_cache
from pathlib import Path
from pydantic import Field, SecretStr, model_validator
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
    chunk_size: int = Field(default=800, ge=100, le=4000)
    chunk_overlap: int = Field(default=100, ge=0)
    max_extracted_chars: int = Field(default=4000000, ge=1000)
    max_document_chunks: int = Field(default=10000, ge=1)
    embedding_batch_size: int = Field(default=16, ge=1, le=128)
    ollama_timeout_seconds: int = Field(default=180, ge=1, le=1800)
    qdrant_timeout_seconds: int = Field(default=30, ge=1)
    retrieval_score_threshold: float = Field(default=0.3, ge=-1, le=1)
    max_context_chars: int = Field(default=16000, ge=1000, le=40000)
    worker_poll_seconds: float = Field(default=2, ge=0.1, le=60)
    top_k: int = Field(default=5, ge=1, le=20)
    max_upload_size_mb: int = Field(default=20, ge=1, le=100)
    storage_dir: Path = BACKEND / "storage"
    cors_origins: list[str] = ["http://localhost:3000"]


    @model_validator(mode="after")
    def validate_chunking(self):
        if self.chunk_overlap >= self.chunk_size:
            raise ValueError("CHUNK_OVERLAP must be smaller than CHUNK_SIZE")
        if self.embedding_model == self.ollama_model:
            raise ValueError("Use a dedicated embedding model, separate from OLLAMA_MODEL")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
