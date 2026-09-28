from datetime import datetime, timedelta, timezone
from sqlalchemy import select
from app.models import VectorCleanup
from app.rag.vector_store import VectorStore


def schedule_cleanup(db, organization_id, chatbot_id, document_id=None):
    db.add(VectorCleanup(organization_id=organization_id, chatbot_id=chatbot_id, document_id=document_id))


def process_cleanup(db, store=None):
    job = db.scalar(select(VectorCleanup).where(VectorCleanup.next_attempt_at <= datetime.now(timezone.utc))
                    .order_by(VectorCleanup.created_at).limit(1))
    if not job:
        db.rollback()
        return False
    try:
        (store or VectorStore()).delete(job.organization_id, job.chatbot_id, job.document_id)
        db.delete(job)
    except Exception:
        job.attempts += 1
        job.error_message = "Vector cleanup is waiting for Qdrant. It will retry automatically."
        job.next_attempt_at = datetime.now(timezone.utc) + timedelta(seconds=min(300, 10 * job.attempts))
    db.commit()
    return True
