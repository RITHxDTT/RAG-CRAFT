from sqlalchemy import select
from app.models.channels import ChannelIntegration
from app.models import Conversation

def list_channels(db,chatbot_id):
    return list(db.scalars(select(ChannelIntegration).where(ChannelIntegration.chatbot_id==chatbot_id).order_by(ChannelIntegration.created_at)))

def public_channel(db,channel,public_id):
    return db.scalar(select(ChannelIntegration).where(ChannelIntegration.channel==channel,ChannelIntegration.public_id==public_id))

def guest_conversation(db,bot_id,integration_id,session_hash,conversation_id=None):
    query=select(Conversation).where(Conversation.chatbot_id==bot_id,Conversation.integration_id==integration_id,Conversation.session_hash==session_hash)
    if conversation_id:
        query=query.where(Conversation.id==conversation_id)
    return db.scalar(query.order_by(Conversation.updated_at.desc()).limit(1))
