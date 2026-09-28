from uuid import UUID
from sqlalchemy import func, select
from app.core.exceptions import AppError
from app.models import Document, DocumentChunk, IngestionJob, VectorCleanup
from app.services import ingestion_service
from app.services.vector_cleanup_service import process_cleanup


class MemoryVectors:
    def __init__(self):
        self.points = {}
    def ensure_collection(self, dimensions):
        assert dimensions == 3
    def delete(self, org, bot, doc=None):
        self.points = {id: point for id, point in self.points.items() if not (
            point['payload']['organization_id'] == str(org) and point['payload']['chatbot_id'] == str(bot)
            and (doc is None or point['payload']['document_id'] == str(doc)))}
    def upsert(self, points):
        self.points.update({p['id']: p for p in points})


def queued_document(client):
    bot = client.post('/api/chatbots', json={'name': 'HR'}).json()['id']
    doc = client.post(f'/api/chatbots/{bot}/documents', files={'file': ('policy.txt', b'Annual leave is 18 days.', 'text/plain')}).json()['id']
    return bot, doc


def test_ingestion_and_reindex_are_idempotent(logged_in, db, monkeypatch):
    bot, doc_id = queued_document(logged_in)
    monkeypatch.setattr(ingestion_service.embedding_service, 'embed', lambda texts: [[1., 0., 0.] for _ in texts])
    store = MemoryVectors()
    item = ingestion_service.claim_next(db)
    assert db.get(Document, UUID(doc_id)).status == 'PROCESSING'
    assert ingestion_service.process_claimed(db, item, store)
    assert db.get(Document, UUID(doc_id)).status == 'READY'
    assert db.scalar(select(func.count(DocumentChunk.id))) == len(store.points) == 1
    point = next(iter(store.points.values()))
    assert point['payload']['chatbot_id'] == bot
    assert point['payload']['organization_id'] == str(item['organization_id'])
    old_id = point['id']
    assert logged_in.post(f'/api/chatbots/{bot}/documents/{doc_id}/reindex').status_code == 200
    assert ingestion_service.process_claimed(db, ingestion_service.claim_next(db), store)
    assert len(store.points) == 1 and old_id not in store.points
    assert db.scalar(select(func.count(DocumentChunk.id))) == 1
    assert logged_in.delete(f'/api/chatbots/{bot}/documents/{doc_id}').status_code == 204
    assert db.scalar(select(VectorCleanup)) is not None
    assert process_cleanup(db, store)
    assert store.points == {}


def test_failure_retry_and_restart_recovery(logged_in, db, monkeypatch):
    bot, doc_id = queued_document(logged_in)
    def fail(_):
        raise AppError(503, 'Ollama unavailable. Retry later.')
    monkeypatch.setattr(ingestion_service.embedding_service, 'embed', fail)
    item = ingestion_service.claim_next(db)
    assert not ingestion_service.process_claimed(db, item, MemoryVectors())
    assert db.get(Document, UUID(doc_id)).status == 'FAILED'
    assert db.get(Document, UUID(doc_id)).error_message == 'Ollama unavailable. Retry later.'
    assert logged_in.post(f'/api/chatbots/{bot}/documents/{doc_id}/retry').status_code == 200
    item = ingestion_service.claim_next(db)
    ingestion_service.recover_interrupted(db)
    assert db.get(Document, UUID(doc_id)).status == 'QUEUED'
    assert db.get(IngestionJob, item['job_id']).status == 'QUEUED'
    monkeypatch.setattr(ingestion_service.embedding_service, 'embed', lambda texts: [[1., 0., 0.] for _ in texts])
    assert ingestion_service.process_claimed(db, ingestion_service.claim_next(db), MemoryVectors())


def test_partial_vector_failure_never_marks_ready(logged_in, db, monkeypatch):
    _, doc_id = queued_document(logged_in)
    monkeypatch.setattr(ingestion_service.embedding_service, 'embed', lambda texts: [[1., 0., 0.] for _ in texts])
    class BrokenStore(MemoryVectors):
        def upsert(self, points):
            super().upsert(points)
            raise AppError(503, 'Qdrant unavailable')
    assert not ingestion_service.process_claimed(db, ingestion_service.claim_next(db), BrokenStore())
    assert db.get(Document, UUID(doc_id)).status == 'FAILED'
    assert db.scalar(select(func.count(DocumentChunk.id))) == 0


def test_cleanup_retries_when_qdrant_is_down(logged_in, db):
    bot, doc_id = queued_document(logged_in)
    logged_in.delete(f'/api/chatbots/{bot}/documents/{doc_id}')
    class BrokenStore:
        def delete(self, *args):
            raise AppError(503, 'Unavailable')
    assert process_cleanup(db, BrokenStore())
    job = db.scalar(select(VectorCleanup))
    assert job.attempts == 1 and job.error_message
