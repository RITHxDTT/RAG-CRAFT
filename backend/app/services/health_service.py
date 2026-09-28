import asyncio
import httpx
from sqlalchemy import text
from sqlalchemy.orm import Session
from app.core.config import get_settings
from app.core.worker_lock import worker_is_running
from app.db.session import get_engine


def check_postgres() -> str:
    try:
        with get_engine().connect() as connection:
            connection.execute(text('SELECT 1'))
        return 'ok'
    except Exception:
        return 'unavailable'


def check_worker() -> str:
    try:
        with Session(get_engine()) as db:
            return 'ok' if worker_is_running(db) else 'unavailable'
    except Exception:
        return 'unavailable'


async def check_http(url: str) -> str:
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(url)
            response.raise_for_status()
        return 'ok'
    except httpx.HTTPError:
        return 'unavailable'


async def check_models():
    config = get_settings()
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(f"{config.ollama_base_url.rstrip('/')}/api/tags")
            response.raise_for_status()
        local = {model['name'] for model in response.json()['models'] if not model.get('remote_host') and not model.get('remote_model')}
        def status(name):
            return 'ok' if (name if ':' in name else name + ':latest') in local else 'unavailable'
        return {'ollama': 'ok', 'generation_model': status(config.ollama_model), 'embedding_model': status(config.embedding_model)}
    except (httpx.HTTPError, ValueError, KeyError, TypeError):
        return {'ollama': 'unavailable', 'generation_model': 'unavailable', 'embedding_model': 'unavailable'}


async def dependency_health() -> dict[str, str]:
    config = get_settings()
    postgres, qdrant, worker, models = await asyncio.gather(
        asyncio.to_thread(check_postgres), check_http(f"{config.qdrant_url.rstrip('/')}/healthz"),
        asyncio.to_thread(check_worker), check_models(),
    )
    return {'api': 'ok', 'postgres': postgres, 'qdrant': qdrant, 'worker': worker, **models}
