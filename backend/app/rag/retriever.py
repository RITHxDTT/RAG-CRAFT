import logging
import math
from dataclasses import dataclass
from uuid import UUID
from sqlalchemy import select
from app.core.config import get_settings
from app.models import Chatbot, Document, DocumentChunk, KnowledgeSource
from app.rag import embedding_service
from app.rag.vector_store import VectorStore
from app.schemas.rag import Source

logger = logging.getLogger(__name__)


@dataclass
class RetrievedChunk:
    source: Source
    content: str


def retrieve(db, organization_id, chatbot_id, question: str, top_k: int, store=None) -> list[RetrievedChunk]:
    ready = list(db.scalars(select(Document.id).join(KnowledgeSource).join(Chatbot)
                           .where(Chatbot.organization_id == organization_id, Chatbot.id == chatbot_id,
                                  Document.status == "READY")))
    if not ready:
        return []
    vector = embedding_service.embed([question], query=True)[0]
    points = (store or VectorStore()).search(organization_id, chatbot_id, vector, ready, top_k)
    scores = {}
    for point in points:
        try:
            score = float(point["score"])
            if math.isfinite(score):
                scores[UUID(str(point["id"]))] = score
        except (KeyError, ValueError, TypeError):
            continue
    if not scores:
        return []
    # Resolve IDs against PostgreSQL rather than trusting vector payload content/ownership.
    rows = db.execute(select(DocumentChunk, Document).join(Document).join(KnowledgeSource).join(Chatbot)
                      .where(DocumentChunk.id.in_(scores), Document.status == "READY",
                             Chatbot.organization_id == organization_id, Chatbot.id == chatbot_id)).all()
    remaining = get_settings().max_context_chars
    result = []
    for chunk, document in sorted(rows, key=lambda row: scores[row[0].id], reverse=True):
        content = chunk.content[:remaining]
        if not content:
            break
        remaining -= len(content)
        result.append(RetrievedChunk(content=content, source=Source(
            chunk_id=chunk.id, document_id=document.id, document_name=document.name,
            page_number=chunk.page_number, chunk_index=chunk.chunk_index,
            excerpt=content[:1000], score=scores[chunk.id],
        )))
    logger.info("Retrieval completed organization_id=%s chatbot_id=%s chunks=%s", organization_id, chatbot_id, len(result))
    return result
