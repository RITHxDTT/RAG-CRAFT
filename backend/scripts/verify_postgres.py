"""Verify migrations and tests in a newly created, disposable PostgreSQL database."""
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))
from app.core.config import get_settings  # noqa: E402


def main():
    source_url = make_url(get_settings().database_url)
    database_name = "ragcraft_test_" + uuid4().hex
    engine = create_engine(source_url, isolation_level="AUTOCOMMIT")
    created = False
    try:
        with engine.connect() as connection:
            connection.execute(text(f'CREATE DATABASE "{database_name}"'))
            created = True
        test_url = source_url.set(database=database_name).render_as_string(hide_password=False)
        env = {**os.environ, "DATABASE_URL": test_url, "TEST_DATABASE_URL": test_url}
        alembic = [sys.executable, "-m", "alembic", "-c", "backend/alembic.ini"]
        # Exercise the V1-to-V2 backfill with a real legacy chatbot, not just empty tables.
        subprocess.run(alembic + ["upgrade", "9bc7c261b302"], cwd=ROOT, env=env, check=True)
        legacy_engine = create_engine(test_url)
        user_id, org_id, bot_id = uuid4(), uuid4(), uuid4()
        with legacy_engine.begin() as connection:
            connection.execute(text("INSERT INTO users(id,email,password_hash,is_active,token_version) VALUES (:id,'legacy@example.test','test-only',true,0)"), {"id": user_id})
            connection.execute(text("INSERT INTO organizations(id,name) VALUES (:id,'Legacy')"), {"id": org_id})
            connection.execute(text("INSERT INTO organization_members(id,user_id,organization_id,role) VALUES (:id,:user,:org,'ADMIN')"), {"id": uuid4(), "user": user_id, "org": org_id})
            connection.execute(text("INSERT INTO chatbots(id,organization_id,name,description,status) VALUES (:id,:org,'Legacy HR','','ACTIVE')"), {"id": bot_id, "org": org_id})
            connection.execute(text("INSERT INTO chatbot_settings(id,chatbot_id,system_instruction,model_name,temperature,answer_length,top_k) VALUES (:id,:bot,'Legacy instruction','legacy:local',0.2,'MEDIUM',5)"), {"id": uuid4(), "bot": bot_id})
        subprocess.run(alembic + ["upgrade", "head"], cwd=ROOT, env=env, check=True)
        with legacy_engine.connect() as connection:
            assert connection.scalar(text("SELECT owner_id FROM chatbots WHERE id=:id"), {"id": bot_id}) == user_id
            assert connection.scalar(text("SELECT role FROM users WHERE id=:id"), {"id": user_id}) == 'ADMIN'
            assert connection.scalar(text("SELECT m.model_identifier FROM models m JOIN chatbot_settings s ON s.model_id=m.id WHERE s.chatbot_id=:id"), {"id": bot_id}) == 'legacy:local'
        legacy_engine.dispose()
        print("Legacy ownership, role, and model backfills passed.", flush=True)
        for action in [["downgrade", "base"], ["upgrade", "head"], ["check"]]:
            subprocess.run(alembic + action, cwd=ROOT, env=env, check=True)
        subprocess.run([sys.executable, "-m", "pytest", "-q"], cwd=ROOT, env=env, check=True)
        print("PostgreSQL migrations and integration tests passed.")
    finally:
        if created:
            with engine.connect() as connection:
                connection.execute(text(f'DROP DATABASE "{database_name}" WITH (FORCE)'))
        engine.dispose()


if __name__ == "__main__":
    main()
