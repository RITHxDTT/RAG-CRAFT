"""Single durable V1 worker: PYTHONPATH=backend python -m app.worker."""
import argparse
import logging
import signal
from threading import Event
from sqlalchemy import text
from sqlalchemy.orm import Session
from app.core.config import get_settings
from app.db.session import get_engine
from app.services.ingestion_service import claim_next, process_claimed, recover_interrupted
from app.services.vector_cleanup_service import process_cleanup

from app.core.worker_lock import WORKER_LOCK
logger = logging.getLogger(__name__)


def run(once=False):
    stop = Event()
    for name in (signal.SIGINT, signal.SIGTERM):
        signal.signal(name, lambda *_: stop.set())
    engine = get_engine()
    # Session-level PostgreSQL advisory lock is released automatically on process/connection loss.
    with engine.connect() as lock:
        if not lock.scalar(text("SELECT pg_try_advisory_lock(:key)"), {"key": WORKER_LOCK}):
            raise RuntimeError("Another ingestion worker is already running for this database.")
        lock.commit()
        try:
            with Session(engine, expire_on_commit=False) as db:
                recover_interrupted(db)
            logger.info("Ingestion worker started")
            while not stop.is_set():
                # Never continue without the database connection that owns the advisory lock.
                lock.execute(text("SELECT 1"))
                lock.commit()
                with Session(engine, expire_on_commit=False) as db:
                    cleaned = process_cleanup(db)
                    item = claim_next(db)
                    if item:
                        process_claimed(db, item)
                if once:
                    break
                if not item and not cleaned:
                    stop.wait(get_settings().worker_poll_seconds)
        finally:
            lock.execute(text("SELECT pg_advisory_unlock(:key)"), {"key": WORKER_LOCK})
            lock.commit()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    logging.getLogger("httpx").setLevel(logging.WARNING)
    parser = argparse.ArgumentParser()
    parser.add_argument("--once", action="store_true", help="Process at most one cleanup and one ingestion job.")
    run(parser.parse_args().once)
