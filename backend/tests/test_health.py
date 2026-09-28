from fastapi.testclient import TestClient
from app.main import app
from app.api import health

client = TestClient(app)

def test_liveness():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"

def test_allowed_origin():
    response = client.get("/health", headers={"Origin": "http://localhost:3000"})
    assert response.headers["access-control-allow-origin"] == "http://localhost:3000"

def test_disallowed_origin():
    response = client.get("/health", headers={"Origin": "https://untrusted.example"})
    assert "access-control-allow-origin" not in response.headers

def test_readiness_reports_failure_without_details(monkeypatch):
    async def unavailable():
        return {"api": "ok", "postgres": "unavailable", "qdrant": "ok", "ollama": "ok"}
    monkeypatch.setattr(health, "dependency_health", unavailable)
    response = client.get("/health/ready")
    assert response.status_code == 503
    assert response.json()["services"]["postgres"] == "unavailable"
