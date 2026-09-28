import asyncio
import httpx
from sqlalchemy import text
from app.core.config import get_settings
from app.db.session import get_engine

def check_postgres() -> str:
    try:
        with get_engine().connect() as connection:
            connection.execute(text("SELECT 1"))
        return "ok"
    except Exception:
        return "unavailable"

async def check_http(url: str) -> str:
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(url)
            response.raise_for_status()
        return "ok"
    except httpx.HTTPError:
        return "unavailable"

async def dependency_health() -> dict[str, str]:
    settings = get_settings()
    postgres, qdrant, ollama = await asyncio.gather(
        asyncio.to_thread(check_postgres),
        check_http(f"{settings.qdrant_url.rstrip('/')}/healthz"),
        check_http(f"{settings.ollama_base_url.rstrip('/')}/api/tags"),
    )
    return {"api": "ok", "postgres": postgres, "qdrant": qdrant, "ollama": ollama}
