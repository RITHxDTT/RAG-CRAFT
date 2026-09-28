import logging
import math
import httpx
from app.core.config import get_settings
from app.core.exceptions import AppError

logger = logging.getLogger(__name__)


def embed(texts: list[str], *, query: bool = False) -> list[list[float]]:
    if not texts:
        return []
    config = get_settings()
    prefix = "search_query: " if query else "search_document: "
    inputs = [prefix + text if config.embedding_model.startswith("nomic-embed-text") else text for text in texts]
    try:
        with httpx.Client(timeout=config.ollama_timeout_seconds) as client:
            response = client.post(f"{config.ollama_base_url.rstrip('/')}/api/embed", json={
                "model": config.embedding_model, "input": inputs, "truncate": False, "keep_alive": "5m",
            })
            response.raise_for_status()
        vectors = response.json()["embeddings"]
        if (len(vectors) != len(texts) or not vectors or not vectors[0]
                or any(len(v) != len(vectors[0]) or not all(isinstance(x, (int, float)) and math.isfinite(x) for x in v) for v in vectors)):
            raise ValueError("Invalid embeddings")
        return vectors
    except httpx.TimeoutException:
        raise AppError(504, "Embedding generation timed out. Retry when Ollama is available.") from None
    except (httpx.HTTPError, ValueError, KeyError, TypeError):
        logger.warning("Embedding generation failed")
        raise AppError(503, "Embedding generation failed. Check Ollama and the configured embedding model, then retry.") from None
