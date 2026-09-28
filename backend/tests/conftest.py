import os
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from app.core.config import get_settings
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.schemas.auth import AdminCreate
from app.services.auth_service import create_admin

HEADERS = {"X-Requested-With": "RAGCraft", "Origin": "http://localhost:3000"}
PASSWORD = "test-password-for-admin"


@pytest.fixture
def db():
    test_url = os.environ.get("TEST_DATABASE_URL")
    if test_url:
        engine = create_engine(test_url)
        with engine.connect() as connection:
            transaction = connection.begin()
            with Session(connection, expire_on_commit=False, join_transaction_mode="create_savepoint") as session:
                yield session
            transaction.rollback()
    else:
        engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        @event.listens_for(engine, "connect")
        def enable_foreign_keys(connection, _):
            connection.execute("PRAGMA foreign_keys=ON")
        Base.metadata.create_all(engine)
        with Session(engine, expire_on_commit=False) as session:
            yield session
    engine.dispose()



@pytest.fixture
def client(db, tmp_path, monkeypatch):
    from app.core.rate_limit import login_limiter
    login_limiter.attempts.clear()
    monkeypatch.setattr(get_settings(), "storage_dir", tmp_path / "uploads")
    def override_db():
        try:
            yield db
        except Exception:
            db.rollback()
            raise
    app.dependency_overrides[get_db] = override_db
    with TestClient(app, headers=HEADERS) as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture
def admin(db):
    return create_admin(db, AdminCreate(email="admin@example.com", password=PASSWORD))


@pytest.fixture
def logged_in(client, admin):
    response = client.post("/api/auth/login", json={"email": admin.email, "password": PASSWORD})
    assert response.status_code == 200, response.text
    return client
