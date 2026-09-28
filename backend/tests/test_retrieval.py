from uuid import UUID, uuid4
from sqlalchemy import select
from app.models import Chatbot, Document, DocumentChunk
from app.rag import retriever
from app.rag.vector_store import VectorStore
from app.services import ingestion_service
from test_ingestion import MemoryVectors, queued_document


def test_search_filter_contains_both_owners_and_ready_documents(monkeypatch):
    store = VectorStore()
    captured = {}
    def request(method, path='', **kwargs):
        captured.update(kwargs['data'])
        return {'points': []}
    monkeypatch.setattr(store, 'request', request)
    org, bot, doc = uuid4(), uuid4(), uuid4()
    assert store.search(org, bot, [1., 0.], [doc], 5) == []
    conditions = {c['key']: c['match'] for c in captured['filter']['must']}
    assert conditions['organization_id'] == {'value': str(org)}
    assert conditions['chatbot_id'] == {'value': str(bot)}
    assert conditions['document_id'] == {'any': [str(doc)]}
    assert 'embedding_model' in conditions


def test_ready_only_and_postgres_rechecks_scope(logged_in, db, monkeypatch):
    bot, doc_id = queued_document(logged_in)
    monkeypatch.setattr(ingestion_service.embedding_service, 'embed', lambda texts, **kwargs: [[1., 0., 0.] for _ in texts])
    ingestion_service.process_claimed(db, ingestion_service.claim_next(db), MemoryVectors())
    chunk = db.scalar(select(DocumentChunk))
    owner = db.get(Chatbot, UUID(bot)).organization_id
    class UntrustedStore:
        def search(self, *args):
            return [{'id': str(chunk.id), 'score': 0.9, 'payload': {'document_name': 'fabricated'}}]
    result = retriever.retrieve(db, owner, UUID(bot), 'Annual leave?', 5, UntrustedStore())
    assert len(result) == 1 and result[0].source.document_name == 'policy.txt'
    # A malicious/stale point cannot cross organizations even if a vector adapter returned it.
    assert retriever.retrieve(db, uuid4(), UUID(bot), 'Annual leave?', 5, UntrustedStore()) == []
    db.get(Document, UUID(doc_id)).status = 'FAILED'
    db.commit()
    assert retriever.retrieve(db, owner, UUID(bot), 'Annual leave?', 5, UntrustedStore()) == []


def test_no_ready_knowledge_does_not_call_ollama(logged_in, db, monkeypatch):
    bot, _ = queued_document(logged_in)
    def unexpected(*args, **kwargs):
        raise AssertionError('No knowledge should not call the model')
    monkeypatch.setattr(retriever.embedding_service, 'embed', unexpected)
    owner = db.get(Chatbot, UUID(bot)).organization_id
    assert retriever.retrieve(db, owner, UUID(bot), 'Question', 5) == []
