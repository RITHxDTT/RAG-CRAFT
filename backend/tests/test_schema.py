from sqlalchemy import create_engine, inspect
from app.db.base import Base
import app.models  # noqa: F401


def test_foundation_has_fourteen_tables_and_foreign_keys():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    inspector = inspect(engine)
    assert len(inspector.get_table_names()) == 14
    assert inspector.get_foreign_keys("chatbots")[0]["referred_table"] == "organizations"
    assert inspector.get_foreign_keys("documents")[0]["referred_table"] == "knowledge_sources"
    assert inspector.get_unique_constraints("knowledge_sources")[0]["column_names"] == ["chatbot_id", "source_type", "source_key"]
    engine.dispose()
