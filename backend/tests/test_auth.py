from datetime import datetime, timedelta, timezone
from sqlalchemy import select
import jwt
from app.core.config import get_settings
from app.core.security import COOKIE_NAME, verify_password
from app.models import Organization, OrganizationMember
from conftest import PASSWORD


def test_admin_has_hashed_password_and_organization(db, admin):
    assert admin.password_hash != PASSWORD
    assert verify_password(PASSWORD, admin.password_hash)
    assert db.scalar(select(Organization)).name == "Default Organization"
    assert db.scalar(select(OrganizationMember)).role == "ADMIN"


def test_login_me_logout_and_cookie_replay(logged_in):
    client = logged_in
    old_cookie = client.cookies.get(COOKIE_NAME)
    assert client.get("/api/auth/me").json()["role"] == "ADMIN"
    assert client.post("/api/auth/logout").status_code == 204
    assert client.get("/api/auth/me").status_code == 401
    client.cookies.set(COOKIE_NAME, old_cookie)
    assert client.get("/api/auth/me").status_code == 401


def test_bad_login_and_unknown_account_are_indistinguishable(client, admin):
    for email in [admin.email, "unknown@example.com"]:
        response = client.post("/api/auth/login", json={"email": email, "password": "incorrect"})
        assert response.status_code == 401
        assert response.json()["detail"] == "Email or password is incorrect."


def test_cookie_flags(client, admin):
    response = client.post("/api/auth/login", json={"email": admin.email, "password": PASSWORD})
    cookie = response.headers["set-cookie"].lower()
    assert "httponly" in cookie and "samesite=lax" in cookie
    assert "password" not in response.text


def test_untrusted_origin_and_missing_header(client, admin):
    data = {"email": admin.email, "password": PASSWORD}
    assert client.post("/api/auth/login", json=data, headers={"Origin": "https://evil.example"}).status_code == 403
    assert client.post("/api/auth/login", json=data, headers={"X-Requested-With": ""}).status_code == 403


def test_expired_and_tampered_sessions(logged_in):
    token = logged_in.cookies.get(COOKIE_NAME)
    claims = jwt.decode(token, options={"verify_signature": False})
    claims["exp"] = datetime.now(timezone.utc) - timedelta(minutes=1)
    expired = jwt.encode(claims, get_settings().jwt_secret.get_secret_value(), algorithm="HS256")
    for invalid in [expired, "invalid-token"]:
        logged_in.cookies.clear()
        logged_in.cookies.set(COOKIE_NAME, invalid)
        assert logged_in.get("/api/auth/me").status_code == 401


def test_disabled_user_and_revoked_membership(logged_in, db, admin):
    admin.is_active = False
    db.commit()
    assert logged_in.get("/api/auth/me").status_code == 401
    admin.is_active = True
    db.scalar(select(OrganizationMember)).role = "MEMBER"
    db.commit()
    assert logged_in.get("/api/auth/me").status_code == 403


def test_validation_does_not_echo_password(client):
    password = "private" * 100
    response = client.post("/api/auth/login", json={"email": "bad", "password": password})
    assert response.status_code == 422
    assert password not in response.text
