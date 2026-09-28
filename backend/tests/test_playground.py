from uuid import uuid4
from app.rag.pipeline import INSUFFICIENT
from app.schemas.rag import RagAnswer, Source
from app.services import playground_service
from app.schemas.auth import AdminCreate
from app.services.auth_service import create_admin
from conftest import PASSWORD


def test_answer_endpoint_returns_structured_citation(logged_in, monkeypatch):
    bot = logged_in.post('/api/chatbots', json={'name':'HR'}).json()['id']
    source = Source(chunk_id=None, document_id=uuid4(), document_name='handbook.pdf', page_number=12,
                    chunk_index=1, excerpt='18 days of annual leave.', score=0.9)
    monkeypatch.setattr(playground_service.pipeline, 'answer', lambda *args: RagAnswer(answer='18 days.', sources=[source]))
    response = logged_in.post(f'/api/chatbots/{bot}/ask', json={'question':'How many leave days?'})
    assert response.status_code == 200
    assert response.json()['sources'][0]['page_number'] == 12
    assert response.json()['answer'] == '18 days.'


def test_empty_chatbot_abstains_and_foreign_bot_is_hidden(logged_in, db):
    bot = logged_in.post('/api/chatbots', json={'name':'HR'}).json()['id']
    path = f'/api/chatbots/{bot}/ask'
    response = logged_in.post(path, json={'question':'Tell me a secret'})
    assert response.status_code == 200
    assert response.json()['answer'] == INSUFFICIENT
    assert response.json()['sources'] == []
    assert logged_in.post(path, json={'question':'   '}).status_code == 422
    create_admin(db, AdminCreate(email='foreign@example.com', password=PASSWORD))
    logged_in.post('/api/auth/login', json={'email':'foreign@example.com','password':PASSWORD})
    assert logged_in.post(path, json={'question':'Question'}).status_code == 404
