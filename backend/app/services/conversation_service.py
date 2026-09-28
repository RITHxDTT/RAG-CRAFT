from app.core.exceptions import AppError
from app.repositories import conversation_repository as conversations
from app.schemas.conversation import ConversationDetail, MessageView
from app.schemas.rag import Source
from app.services.chatbot_service import require_chatbot


def require_conversation(db, ctx, chatbot_id, conversation_id):
    conversation = conversations.get_conversation(db, ctx.organization_id, chatbot_id, conversation_id)
    if not conversation:
        raise AppError(404, 'Conversation not found.')
    return conversation


def list_conversations(db, ctx, chatbot_id):
    require_chatbot(db, ctx, chatbot_id)
    return conversations.list_conversations(db, ctx.organization_id, chatbot_id)


def detail(db, ctx, chatbot_id, conversation_id):
    conversation = require_conversation(db, ctx, chatbot_id, conversation_id)
    messages = conversations.messages(db, conversation.id)
    grouped = {}
    for source in conversations.sources(db, [m.id for m in messages]):
        grouped.setdefault(source.message_id, []).append(Source(
            chunk_id=source.document_chunk_id, document_id=source.document_id,
            document_name=source.document_name, page_number=source.page_number,
            chunk_index=source.chunk_index, excerpt=source.excerpt or '', score=source.score or 0,
        ))
    return ConversationDetail(id=conversation.id, chatbot_id=conversation.chatbot_id, title=conversation.title,
        created_at=conversation.created_at, updated_at=conversation.updated_at,
        messages=[MessageView(id=m.id, role=m.role, content=m.content, created_at=m.created_at,
                              sources=grouped.get(m.id, [])) for m in messages])


def delete_conversation(db, ctx, chatbot_id, conversation_id):
    require_chatbot(db, ctx, chatbot_id, lock=True)
    db.delete(require_conversation(db, ctx, chatbot_id, conversation_id))
    db.commit()
