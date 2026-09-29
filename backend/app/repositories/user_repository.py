from sqlalchemy import select, update
from sqlalchemy.orm import Session
from app.models import User, OrganizationMember


def find_by_email(db: Session, email: str):
    return db.scalar(select(User).where(User.email == email))


def admin_membership(db: Session, user_id, organization_id=None):
    query = select(OrganizationMember).where(OrganizationMember.user_id == user_id,
                                              OrganizationMember.role == "ADMIN")
    if organization_id is not None:
        query = query.where(OrganizationMember.organization_id == organization_id)
    return db.scalar(query.order_by(OrganizationMember.created_at, OrganizationMember.id).limit(1))


def revoke_sessions(db: Session, user_id):
    db.execute(update(User).where(User.id == user_id).values(token_version=User.token_version + 1))


def membership(db, user_id, organization_id=None):
    user = db.get(User, user_id)
    query = select(OrganizationMember).where(OrganizationMember.user_id == user_id)
    if user.role == "ADMIN":
        query = query.where(OrganizationMember.role == "ADMIN")
    if organization_id is not None:
        query = query.where(OrganizationMember.organization_id == organization_id)
    return db.scalar(query.order_by(OrganizationMember.created_at, OrganizationMember.id).limit(1))
