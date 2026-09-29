import logging
from dataclasses import dataclass
from uuid import UUID
from sqlalchemy.orm import Session
from app.core.exceptions import AppError
from app.core.security import DUMMY_HASH, create_token, decode_token, hash_password, verify_password
from app.models import User, Organization, OrganizationMember, AuditLog
from app.repositories import user_repository as users
from app.schemas.auth import AdminCreate, CurrentUser, LoginInput

logger = logging.getLogger(__name__)


@dataclass
class UserContext:
    user: User
    organization: Organization

    @property
    def organization_id(self):
        return self.organization.id


# Compatibility for existing V1 scripts and service annotations.
AdminContext = UserContext

def profile(ctx: AdminContext) -> CurrentUser:
    return CurrentUser(id=ctx.user.id, email=ctx.user.email, organization_id=ctx.organization_id,
                       organization_name=ctx.organization.name, role=ctx.user.role, full_name=ctx.user.full_name)


def create_admin(db: Session, data: AdminCreate) -> User:
    if users.find_by_email(db, data.email):
        raise AppError(409, "An account with that email already exists.")
    user = User(email=data.email, password_hash=hash_password(data.password), role="ADMIN")
    organization = Organization(name=data.organization_name.strip())
    if not organization.name:
        raise AppError(422, "Organization name cannot be blank.")
    db.add_all([user, organization])
    db.flush()
    db.add(OrganizationMember(user_id=user.id, organization_id=organization.id, role="ADMIN"))
    db.add(AuditLog(user_id=user.id, organization_id=organization.id, action="admin.created",
                    resource_type="user", resource_id=user.id))
    db.commit()
    return user


def login(db: Session, data: LoginInput) -> tuple[CurrentUser, str]:
    user = users.find_by_email(db, data.email)
    valid = verify_password(data.password, user.password_hash if user else DUMMY_HASH)
    if not valid or not user or not user.is_active:
        raise AppError(401, "Email or password is incorrect.")
    membership = users.membership(db, user.id)
    if not membership:
        raise AppError(403, "Workspace membership is required.")
    organization = db.get(Organization, membership.organization_id)
    ctx = AdminContext(user, organization)
    token = create_token(user.id, organization.id, user.token_version)
    db.add(AuditLog(user_id=user.id, organization_id=organization.id, action="user.login",
                    resource_type="user", resource_id=user.id))
    db.commit()
    logger.info("User login user_id=%s", user.id)
    return profile(ctx), token


def authenticate(db: Session, token: str | None) -> AdminContext:
    if not token:
        raise AppError(401, "Please log in to continue.")
    claims = decode_token(token)
    try:
        user_id, organization_id = UUID(claims["sub"]), UUID(claims["org"])
    except (ValueError, TypeError, KeyError):
        raise AppError(401, "Invalid session.") from None
    user = db.get(User, user_id)
    if not user or not user.is_active or user.token_version != claims["ver"]:
        raise AppError(401, "Please log in again.")
    if not users.membership(db, user.id, organization_id):
        raise AppError(403, "Workspace membership is required.")
    return AdminContext(user, db.get(Organization, organization_id))


def logout(db: Session, ctx: AdminContext):
    # V1 logout invalidates all this user's sessions, including copied cookies.
    users.revoke_sessions(db, ctx.user.id)
    db.commit()


def register(db, data):
    if users.find_by_email(db, data.email):
        raise AppError(409, "An account with that email already exists.")
    user = User(email=data.email, full_name=data.full_name, role="USER", password_hash=hash_password(data.password))
    org = Organization(name=f"{data.full_name[:100]}'s workspace")
    db.add_all([user, org])
    db.flush()
    db.add(OrganizationMember(user_id=user.id, organization_id=org.id, role="MEMBER"))
    db.commit()
    logger.info("User registered user_id=%s", user.id)
    return login(db, LoginInput(email=data.email, password=data.password))


def update_profile(db, ctx, data):
    ctx.user.full_name = data.full_name
    db.commit()
    return profile(ctx)
