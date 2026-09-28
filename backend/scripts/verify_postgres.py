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
        for action in [["upgrade", "head"], ["downgrade", "base"], ["upgrade", "head"], ["check"]]:
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
