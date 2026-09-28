import logging
import httpx
from pydantic import ValidationError
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.schemas.rag import GeneratedAnswer

logger = logging.getLogger(__name__)


def generate(messages: list[dict], settings) -> GeneratedAnswer:
    config = get_settings()
    try:
        with httpx.Client(timeout=config.ollama_timeout_seconds) as client:
            # V1 promises local inference: never silently forward private knowledge to a cloud model.
            tags = client.get(f"{config.ollama_base_url.rstrip('/')}/api/tags")
            tags.raise_for_status()
            name = settings.model_name if ':' in settings.model_name else settings.model_name + ':latest'
            model = next((model for model in tags.json()['models'] if model['name'] == name), None)
            if not model or model.get('remote_host') or model.get('remote_model'):
                raise AppError(503, "The configured generation model is not available locally. Pull it with Ollama or choose a local model.")
            response = client.post(f"{config.ollama_base_url.rstrip('/')}/api/chat", json={
                "model": settings.model_name, "messages": messages, "stream": False,
                "format": GeneratedAnswer.model_json_schema(), "keep_alive": "5m",
                "options": {"temperature": settings.temperature, "num_ctx": 8192,
                            "num_predict": {"SHORT": 200, "MEDIUM": 400, "LONG": 800}[settings.answer_length]},
            })
            response.raise_for_status()
        return GeneratedAnswer.model_validate_json(response.json()["message"]["content"])
    except AppError:
        raise
    except httpx.TimeoutException:
        logger.warning("Ollama generation timed out")
        raise AppError(504, "Answer generation timed out. Try again or ask a shorter question.") from None
    except (httpx.HTTPError, ValidationError, ValueError, KeyError, TypeError):
        logger.warning("Ollama generation failed")
        raise AppError(503, "Ollama could not generate a valid answer. Check the local model and retry.") from None
