from sqlalchemy import text

WORKER_LOCK = 73492011


def worker_is_running(db):
    if db.get_bind().dialect.name != 'postgresql':
        return False
    return bool(db.scalar(text('''SELECT EXISTS (
        SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND granted
        AND classid = 0 AND objid = :key AND objsubid = 1
        AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
    )'''), {'key': WORKER_LOCK}))
