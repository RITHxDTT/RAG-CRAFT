from sqlalchemy import select, update
from app.models.catalog import PlatformModel, PromptTemplate

def list_models(db, enabled_only=False):
    query = select(PlatformModel).order_by(PlatformModel.created_at, PlatformModel.id)
    if enabled_only:
        query = query.where(PlatformModel.enabled.is_(True))
    return list(db.scalars(query))

def list_prompts(db, enabled_only=False):
    query = select(PromptTemplate).order_by(PromptTemplate.name, PromptTemplate.id)
    if enabled_only:
        query = query.where(PromptTemplate.enabled.is_(True))
    return list(db.scalars(query))

def clear_default(db):
    db.execute(update(PlatformModel).where(PlatformModel.is_default.is_(True)).values(is_default=False))

def default_model(db):
    return db.scalar(select(PlatformModel).where(PlatformModel.enabled.is_(True)).order_by(PlatformModel.is_default.desc(), PlatformModel.created_at).limit(1))
