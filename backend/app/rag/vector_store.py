from urllib.parse import quote
import httpx
from app.core.config import get_settings
from app.core.exceptions import AppError


class VectorStore:
    def __init__(self):
        config = get_settings()
        self.url = f"{config.qdrant_url.rstrip('/')}/collections/{quote(config.qdrant_collection, safe='')}"
        self.timeout = config.qdrant_timeout_seconds

    def request(self, method, path="", *, data=None, missing_ok=False):
        try:
            with httpx.Client(timeout=self.timeout) as client:
                response = client.request(method, self.url + path, json=data)
            if missing_ok and response.status_code == 404:
                return None
            response.raise_for_status()
            return response.json().get("result")
        except (httpx.HTTPError, ValueError):
            raise AppError(503, "Vector storage is unavailable. Check Qdrant and retry.") from None

    def ensure_collection(self, dimensions: int):
        info = self.request("GET", missing_ok=True)
        if info is None:
            self.request("PUT", data={"vectors": {"size": dimensions, "distance": "Cosine"}})
            for key in ("organization_id", "chatbot_id", "document_id", "embedding_model"):
                self.request("PUT", "/index?wait=true", data={"field_name": key, "field_schema": "keyword"})
        elif info["config"]["params"]["vectors"].get("size") != dimensions:
            raise AppError(409, "Embedding dimensions differ from the Qdrant collection. Configure a new collection and re-index documents.")

    @staticmethod
    def scope(organization_id, chatbot_id, document_id=None):
        must = [{"key": "organization_id", "match": {"value": str(organization_id)}},
                {"key": "chatbot_id", "match": {"value": str(chatbot_id)}}]
        if document_id is not None:
            must.append({"key": "document_id", "match": {"value": str(document_id)}})
        return {"must": must}

    def delete(self, organization_id, chatbot_id, document_id=None):
        return self.request("POST", "/points/delete?wait=true",
                            data={"filter": self.scope(organization_id, chatbot_id, document_id)}, missing_ok=True)

    def upsert(self, points: list[dict]):
        for start in range(0, len(points), 64):
            self.request("PUT", "/points?wait=true", data={"points": points[start:start + 64]})

    def search(self, organization_id, chatbot_id, vector, ready_document_ids, top_k):
        if not ready_document_ids:
            return []
        config = get_settings()
        scope = self.scope(organization_id, chatbot_id)
        # READY IDs exclude deleted, failed, and half-indexed documents during the search itself.
        scope["must"].extend([
            {"key": "document_id", "match": {"any": [str(id) for id in ready_document_ids]}},
            {"key": "embedding_model", "match": {"value": config.embedding_model}},
        ])
        result = self.request("POST", "/points/query", data={"query": vector, "filter": scope,
                              "limit": top_k, "with_payload": True, "score_threshold": config.retrieval_score_threshold}, missing_ok=True)
        if result is None:
            return []
        if not isinstance(result, dict) or not isinstance(result.get("points"), list):
            raise AppError(503, "Qdrant returned an invalid retrieval response. Please retry.")
        return result["points"]
