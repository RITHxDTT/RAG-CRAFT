"""Exercise the running app with a temporary admin and remove its records afterward."""
from pathlib import Path
import os
import secrets
import sys
import time
from uuid import uuid4
import httpx
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))
from app.db.session import get_engine  # noqa: E402
from app.models import AuditLog, Chatbot, Organization, OrganizationMember, User  # noqa: E402
from app.schemas.auth import AdminCreate  # noqa: E402
from app.services.auth_service import AdminContext, create_admin  # noqa: E402
from app.services.chatbot_service import delete_chatbot  # noqa: E402


def expect(response, status):
    if response.status_code != status:
        raise AssertionError(f"{response.request.method} {response.request.url.path}: expected {status}, got {response.status_code}")
    return response


def wait_until_idle(client, path):
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        if expect(client.get(path), 200).json()["status"] in {"READY", "FAILED"}:
            return
        time.sleep(0.5)
    raise AssertionError("Start the worker and retry: document processing did not finish.")


def main():
    email = f"smoke-{uuid4().hex}@example.com"
    password = secrets.token_urlsafe(32)
    with Session(get_engine(), expire_on_commit=False) as db:
        user = create_admin(db, AdminCreate(email=email, password=password, organization_name="Temporary smoke test"))
        organization_id = db.scalar(select(OrganizationMember.organization_id).where(OrganizationMember.user_id == user.id))
        try:
            with httpx.Client(base_url=os.environ.get("API_URL", "http://localhost:8000"),
                              headers={"X-Requested-With": "RAGCraft", "Origin": "http://localhost:3000"}, timeout=30) as client:
                expect(client.get("/health"), 200)
                expect(client.get("/api/chatbots"), 401)
                expect(client.post("/api/auth/login", json={"email": email, "password": password}), 200)
                expect(client.get("/api/auth/me"), 200)
                bot = expect(client.post("/api/chatbots", json={"name": "Smoke HR Assistant"}), 201).json()
                other = expect(client.post("/api/chatbots", json={"name": "Smoke IT Assistant"}), 201).json()
                root = f'/api/chatbots/{bot["id"]}'
                expect(client.patch(root, json={"description": "Live HTTP verification"}), 200)
                content = b"Employees receive 18 days of annual leave."
                doc = expect(client.post(root + "/documents", files={"file": ("handbook.txt", content, "text/plain")}), 201).json()
                assert doc["status"] == "QUEUED"
                doc_path = root + f'/documents/{doc["id"]}'
                expect(client.get(doc_path), 200)
                assert expect(client.get(doc_path + "/download"), 200).content == content
                expect(client.post(root + "/documents", files={"file": ("duplicate.txt", content, "text/plain")}), 409)
                expect(client.get(f'/api/chatbots/{other["id"]}/documents/{doc["id"]}/download'), 404)
                assert expect(client.get("/api/dashboard"), 200).json()["total_documents"] == 1
                wait_until_idle(client, doc_path)
                expect(client.delete(doc_path), 204)
                expect(client.delete(root), 204)
                expect(client.delete(f'/api/chatbots/{other["id"]}'), 204)
                expect(client.post("/api/auth/logout"), 204)
                expect(client.get("/api/auth/me"), 401)
                page = expect(client.get(os.environ.get("FRONTEND_URL", "http://localhost:3000")), 200)
                assert "RAG Craft" in page.text
            print("Live login, chatbot CRUD, upload/download, duplicate detection, isolation, deletion, logout, and frontend HTTP checks passed.")
        finally:
            db.rollback()
            organization = db.get(Organization, organization_id)
            ctx = AdminContext(db.get(User, user.id), organization)
            for bot_id in list(db.scalars(select(Chatbot.id).where(Chatbot.organization_id == organization_id))):
                delete_chatbot(db, ctx, bot_id)
            db.execute(delete(AuditLog).where(AuditLog.organization_id == organization_id))
            db.execute(delete(Organization).where(Organization.id == organization_id))
            db.execute(delete(User).where(User.id == user.id))
            db.commit()
            print("Temporary smoke-test account, organization, and files cleaned up.")


if __name__ == "__main__":
    main()
