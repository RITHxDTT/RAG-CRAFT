from app.services.catalog_service import configure
from app.services.vector_cleanup_service import schedule_cleanup
import logging
from sqlalchemy.orm import Session
from app.core.exceptions import AppError
from app.models import AuditLog, Chatbot, ChatbotSetting
from app.repositories import chatbot_repository as bots
from app.repositories import document_repository as documents
from app.services.file_storage import staged_deletion
from app.schemas.chatbot import BotSettings, ChatbotCreate, ChatbotUpdate, ChatbotView, Dashboard
from app.services.auth_service import AdminContext

logger = logging.getLogger(__name__)


def require_chatbot(db: Session, ctx: AdminContext, chatbot_id, *, lock=False):
    bot = bots.get_chatbot(db, ctx.organization_id, chatbot_id, lock=lock)
    if not bot:
        raise AppError(404, "Chatbot not found.")
    if bot.owner_id != ctx.user.id:
        raise AppError(403, "You do not own this chatbot.")
    return bot


def list_chatbots(db: Session, ctx: AdminContext) -> list[ChatbotView]:
    counts = {}
    for chatbot_id, status, count in bots.document_counts(db, ctx.organization_id, ctx.user.id):
        counts.setdefault(chatbot_id, {})[status] = count
    result = []
    for bot in bots.list_chatbots(db, ctx.organization_id, ctx.user.id):
        view = ChatbotView.model_validate(bot)
        statuses = counts.get(bot.id, {})
        view.document_count = sum(statuses.values())
        view.ready_count = statuses.get("READY", 0)
        view.failed_count = statuses.get("FAILED", 0)
        result.append(view)
    return result


def view_chatbot(db: Session, ctx: AdminContext, chatbot_id) -> ChatbotView:
    require_chatbot(db, ctx, chatbot_id)
    return next(bot for bot in list_chatbots(db, ctx) if bot.id == chatbot_id)


def audit(db, ctx, action, resource_id):
    db.add(AuditLog(user_id=ctx.user.id, organization_id=ctx.organization_id,
                    action=action, resource_type="chatbot", resource_id=resource_id))


def create_chatbot(db: Session, ctx: AdminContext, data: ChatbotCreate):
    bot = Chatbot(organization_id=ctx.organization_id, owner_id=ctx.user.id, **data.model_dump(exclude={"settings"}))
    bot.settings = ChatbotSetting(**configure(db, data.settings, creating=True))
    db.add(bot)
    db.flush()
    audit(db, ctx, "chatbot.created", bot.id)
    db.commit()
    logger.info("Chatbot created chatbot_id=%s organization_id=%s", bot.id, ctx.organization_id)
    return ChatbotView.model_validate(bot)


def update_chatbot(db: Session, ctx: AdminContext, chatbot_id, data: ChatbotUpdate):
    bot = require_chatbot(db, ctx, chatbot_id, lock=True)
    for key, value in data.model_dump(exclude_unset=True, exclude={"settings"}).items():
        setattr(bot, key, value)
    if data.settings is not None:
        current = BotSettings.model_validate(bot.settings).model_dump()
        changes = data.settings.model_dump(exclude_unset=True)
        if 'model_name' in changes and 'model_id' not in changes:
            current['model_id'] = None
        current.update(changes)
        for key, value in configure(db, BotSettings(**current)).items():
            setattr(bot.settings, key, value)
    audit(db, ctx, "chatbot.updated", bot.id)
    db.commit()
    return view_chatbot(db, ctx, chatbot_id)


def delete_chatbot(db: Session, ctx: AdminContext, chatbot_id):
    bot = require_chatbot(db, ctx, chatbot_id, lock=True)
    from app.repositories.channel_repository import list_channels
    if any(row.channel == "TELEGRAM" for row in list_channels(db, chatbot_id)):
        raise AppError(409, "Disconnect Telegram in Channels before deleting this chatbot.")
    docs = documents.list_documents(db, ctx.organization_id, chatbot_id)
    if any(doc.status == "PROCESSING" for doc in docs):
        raise AppError(409, "Wait for document processing to finish before deleting this chatbot.")
    with staged_deletion([doc.storage_key for doc in docs]):
        audit(db, ctx, "chatbot.deleted", bot.id)
        schedule_cleanup(db, ctx.organization_id, chatbot_id)
        db.delete(bot)
        db.commit()


def dashboard(db: Session, ctx: AdminContext) -> Dashboard:
    chatbots = list_chatbots(db, ctx)
    return Dashboard(total_chatbots=len(chatbots), total_documents=sum(b.document_count for b in chatbots),
                     ready_documents=sum(b.ready_count for b in chatbots),
                     failed_documents=sum(b.failed_count for b in chatbots), recent_chatbots=chatbots[:5])
