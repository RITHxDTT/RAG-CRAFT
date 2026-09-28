from pathlib import Path
from pypdf import PdfReader
from app.rag.loaders.base import ExtractedPage


def load(path: Path):
    reader = PdfReader(path)
    if reader.is_encrypted:
        raise ValueError("Encrypted PDF")
    for number, page in enumerate(reader.pages, start=1):
        yield ExtractedPage(page.extract_text() or "", number)
