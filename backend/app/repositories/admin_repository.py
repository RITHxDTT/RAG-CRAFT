from sqlalchemy import select,func
from app.models import User,Chatbot,Document,KnowledgeSource,Conversation,Message


def users(db,search='',limit=100,offset=0):
    counts=select(Chatbot.owner_id,func.count(Chatbot.id).label('chatbot_count')).group_by(Chatbot.owner_id).subquery()
    query=select(User,func.coalesce(counts.c.chatbot_count,0)).outerjoin(counts,counts.c.owner_id==User.id)
    if search:
        escaped=search.replace('\\','\\\\').replace('%','\\%').replace('_','\\_')
        query=query.where(User.email.ilike('%'+escaped+'%',escape='\\')|User.full_name.ilike('%'+escaped+'%',escape='\\'))
    return db.execute(query.order_by(User.created_at.desc(),User.id).limit(limit).offset(offset)).all()


def monitored_chatbots(db,limit=100,offset=0):
    return db.execute(select(Chatbot,User.email,User.full_name).join(User,User.id==Chatbot.owner_id).order_by(Chatbot.created_at.desc(),Chatbot.id).limit(limit).offset(offset)).all()


def analytics(db,owner_id=None):
    scope=select(Chatbot.id)
    if owner_id:
        scope=scope.where(Chatbot.owner_id==owner_id)
    conversations=select(Conversation.id).where(Conversation.chatbot_id.in_(scope))
    result={
        'total_chatbots':db.scalar(select(func.count()).select_from(Chatbot).where(Chatbot.id.in_(scope))),
        'total_documents':db.scalar(select(func.count()).select_from(Document).join(KnowledgeSource).where(KnowledgeSource.chatbot_id.in_(scope))),
        'total_conversations':db.scalar(select(func.count()).select_from(Conversation).where(Conversation.id.in_(conversations))),
        'total_messages':db.scalar(select(func.count()).select_from(Message).where(Message.conversation_id.in_(conversations))),
        'messages_by_channel':dict(db.execute(select(Conversation.channel,func.count(Message.id)).join(Message).where(Conversation.chatbot_id.in_(scope)).group_by(Conversation.channel)).all()),
    }
    if owner_id is None:
        result['total_users']=db.scalar(select(func.count()).select_from(User))
        result['active_users']=db.scalar(select(func.count()).select_from(User).where(User.is_active.is_(True)))
    return result
