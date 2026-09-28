from sqlalchemy import select
from app.models import Chatbot, ChatbotSetting
from app.schemas.auth import AdminCreate
from app.services.auth_service import create_admin
from conftest import PASSWORD


def test_chatbot_crud_and_settings(logged_in, db):
    response = logged_in.post("/api/chatbots", json={"name": "HR Assistant", "description": "HR policies"})
    assert response.status_code == 201
    bot = response.json()
    assert bot["settings"]["model_name"] == "llama3.2:3b"
    assert db.scalar(select(ChatbotSetting)).chatbot_id == db.scalar(select(Chatbot)).id
    path = f'/api/chatbots/{bot["id"]}'
    assert logged_in.get(path).json()["name"] == "HR Assistant"
    assert len(logged_in.get("/api/chatbots").json()) == 1
    changed = logged_in.patch(path, json={"name": "People Assistant", "settings": {"top_k": 3}})
    assert changed.status_code == 200
    assert changed.json()["settings"]["top_k"] == 3
    assert logged_in.get("/api/dashboard").json()["total_chatbots"] == 1
    assert logged_in.delete(path).status_code == 204
    assert logged_in.get(path).status_code == 404
    assert db.scalar(select(ChatbotSetting)) is None


def test_other_organization_cannot_access_or_mutate_chatbot(logged_in, db):
    bot_id = logged_in.post("/api/chatbots", json={"name": "Private HR"}).json()["id"]
    create_admin(db, AdminCreate(email="other@example.com", password=PASSWORD))
    assert logged_in.post("/api/auth/login", json={"email": "other@example.com", "password": PASSWORD}).status_code == 200
    path = f"/api/chatbots/{bot_id}"
    assert logged_in.get("/api/chatbots").json() == []
    assert logged_in.get("/api/dashboard").json()["total_chatbots"] == 0
    assert logged_in.get(path).status_code == 404
    assert logged_in.patch(path, json={"name": "Stolen"}).status_code == 404
    assert logged_in.delete(path).status_code == 404


def test_chatbot_input_validation_and_unauthorized(client, logged_in):
    for data in [{"name": "   "}, {"name": "A", "settings": {"top_k": 0}}, {"name": "A", "status": "UNKNOWN"}, {"name": "A", "organization_id": "fake"}]:
        assert logged_in.post("/api/chatbots", json=data).status_code == 422
    bot_id = logged_in.post("/api/chatbots", json={"name": "Bot"}).json()["id"]
    assert logged_in.patch(f"/api/chatbots/{bot_id}", json={"name": None}).status_code == 422
    client.cookies.clear()
    assert client.get("/api/chatbots").status_code == 401
