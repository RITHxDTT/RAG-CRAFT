from app.services.chatbot_service import require_chatbot
from app.services.conversation_service import require_conversation
from app.services import chat_service
from app.core.exceptions import AppError


def ask(db, ctx, chatbot_id, data):
    bot = require_chatbot(db, ctx, chatbot_id, lock=True)
    conversation = require_conversation(db, ctx, chatbot_id, data.conversation_id) if data.conversation_id else None
    if conversation and conversation.channel != 'PLAYGROUND':
        raise AppError(403, 'External conversations are read-only in the Playground.')
    return chat_service.ask(db, bot, data, conversation=conversation)
