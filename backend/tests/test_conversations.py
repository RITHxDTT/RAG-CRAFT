from uuid import UUID
from sqlalchemy import func, select
from app.core.exceptions import AppError
from app.models import Conversation, DocumentChunk, Message
from app.schemas.rag import RagAnswer, Source
from app.services import playground_service
from app.schemas.auth import AdminCreate
from app.services.auth_service import create_admin
from conftest import PASSWORD
from test_ingestion import queued_document


def test_messages_and_citations_survive_reload_and_document_deletion(logged_in, db, monkeypatch):
    bot, doc = queued_document(logged_in)
    chunk = DocumentChunk(document_id=UUID(doc), chunk_index=0, page_number=12, content='18 annual leave days.')
    db.add(chunk)
    db.commit()
    source = Source(chunk_id=chunk.id, document_id=UUID(doc), document_name='policy.txt', page_number=12,
                    chunk_index=0, excerpt=chunk.content, score=0.9)
    seen_history = []
    def respond(db, bot, question, history):
        seen_history.append(history)
        return RagAnswer(answer='18 days.', sources=[source])
    monkeypatch.setattr(playground_service.pipeline, 'answer', respond)
    first = logged_in.post(f'/api/chatbots/{bot}/ask', json={'question':'How much leave?'}).json()
    conversation = first['conversation_id']
    second = logged_in.post(f'/api/chatbots/{bot}/ask', json={'question':'And per year?', 'conversation_id': conversation})
    assert second.status_code == 200 and len(seen_history[-1]) == 2
    path = f'/api/chatbots/{bot}/conversations/{conversation}'
    detail = logged_in.get(path).json()
    assert [m['role'] for m in detail['messages']] == ['USER','ASSISTANT','USER','ASSISTANT']
    assert detail['messages'][1]['sources'][0]['page_number'] == 12
    assert logged_in.delete(f'/api/chatbots/{bot}/documents/{doc}').status_code == 204
    snapshot = logged_in.get(path).json()['messages'][1]['sources'][0]
    assert snapshot['chunk_id'] is None and snapshot['document_id'] == doc
    assert snapshot['excerpt'] == '18 annual leave days.'
    assert logged_in.delete(path).status_code == 204
    assert db.scalar(select(func.count(Message.id))) == 0


def test_conversation_ids_cannot_cross_chatbots_or_organizations(logged_in, db):
    first = logged_in.post('/api/chatbots', json={'name':'A'}).json()['id']
    other = logged_in.post('/api/chatbots', json={'name':'B'}).json()['id']
    conversation = logged_in.post(f'/api/chatbots/{first}/ask', json={'question':'Unknown'}).json()['conversation_id']
    path = f'/api/chatbots/{other}/conversations/{conversation}'
    assert logged_in.get(path).status_code == 404
    assert logged_in.delete(path).status_code == 404
    assert logged_in.post(f'/api/chatbots/{other}/ask', json={'question':'Unknown','conversation_id':conversation}).status_code == 404
    create_admin(db, AdminCreate(email='other@example.com', password=PASSWORD))
    logged_in.post('/api/auth/login', json={'email':'other@example.com','password':PASSWORD})
    assert logged_in.get(f'/api/chatbots/{first}/conversations').status_code == 404
    assert logged_in.get(f'/api/chatbots/{first}/conversations/{conversation}').status_code == 404


def test_generation_failure_does_not_save_partial_turn(logged_in, db, monkeypatch):
    bot = logged_in.post('/api/chatbots', json={'name':'A'}).json()['id']
    def unavailable(*args):
        raise AppError(504, 'Generation timed out.')
    monkeypatch.setattr(playground_service.pipeline, 'answer', unavailable)
    response = logged_in.post(f'/api/chatbots/{bot}/ask', json={'question':'Question'})
    assert response.status_code == 504
    assert db.scalar(select(func.count(Conversation.id))) == 0
    assert db.scalar(select(func.count(Message.id))) == 0
