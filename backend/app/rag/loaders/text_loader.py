from pathlib import Path
from app.rag.loaders.base import ExtractedPage


def load(path: Path):
    yield ExtractedPage(path.read_text(encoding="utf-8-sig"))
