import hashlib
import secrets
import smtplib
from email.message import EmailMessage
from datetime import datetime, timedelta, timezone
from sqlalchemy import select, delete
from app.models import PasswordReset, User
from app.repositories import user_repository as users
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.core.security import hash_password


def send_reset(email, token):
    config = get_settings()
    message = EmailMessage()
    message['Subject'] = 'Reset your RAG Craft password'
    message['From'], message['To'] = config.smtp_from, email
    message.set_content(f"Reset your password within 30 minutes: {config.frontend_url.rstrip('/')}/reset-password#token={token}")
    try:
        with smtplib.SMTP(config.smtp_host, config.smtp_port, timeout=15) as client:
            if config.smtp_starttls:
                client.starttls()
            if config.smtp_username:
                client.login(config.smtp_username, config.smtp_password.get_secret_value() if config.smtp_password else '')
            client.send_message(message)
    except (OSError, smtplib.SMTPException):
        raise AppError(503, 'Password reset email is temporarily unavailable.') from None


def forgot(db, email):
    if not get_settings().smtp_host:
        raise AppError(503, 'Password reset email is not configured. Contact the administrator.')
    user = users.find_by_email(db, email)
    if not user or not user.is_active:
        return
    # Serialize issuance and redemption through the user row.
    db.scalar(select(User).where(User.id == user.id).with_for_update())
    token = secrets.token_urlsafe(32)
    db.execute(delete(PasswordReset).where(PasswordReset.user_id == user.id))
    db.add(PasswordReset(user_id=user.id, token_hash=hashlib.sha256(token.encode()).hexdigest(),
                         expires_at=datetime.now(timezone.utc) + timedelta(minutes=30)))
    send_reset(email, token)
    db.commit()


def reset(db, data):
    digest = hashlib.sha256(data.token.encode()).hexdigest()
    row = db.scalar(select(PasswordReset).where(PasswordReset.token_hash == digest))
    if not row:
        raise AppError(400, 'This reset link is invalid or expired.')
    user = db.scalar(select(User).where(User.id == row.user_id).with_for_update())
    row = db.scalar(select(PasswordReset).where(PasswordReset.token_hash == digest).execution_options(populate_existing=True))
    if not row or row.expires_at.replace(tzinfo=timezone.utc) <= datetime.now(timezone.utc) or not user.is_active:
        raise AppError(400, 'This reset link is invalid or expired.')
    user.password_hash = hash_password(data.password)
    users.revoke_sessions(db, user.id)
    db.execute(delete(PasswordReset).where(PasswordReset.user_id == user.id))
    db.commit()
