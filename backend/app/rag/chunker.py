from dataclasses import dataclass
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.rag.cleaner import clean_text
from app.rag.loaders.base import ExtractedPage


@dataclass(frozen=True)
class TextChunk:
    content: str
    chunk_index: int
    page_number: int | None
    sheet_name: str | None = None
    row_number: int | None = None


def split_boundary(window: str, minimum: int, separators=("\n\n", "\n", ". ", " ")) -> int:
    """Recursively fall back from paragraphs to lines, sentences, words, then characters."""
    if not separators:
        return len(window)
    separator = separators[0]
    index = window.rfind(separator)
    if index >= minimum:
        return index + len(separator)
    return split_boundary(window, minimum, separators[1:])


def chunk_pages(pages: list[ExtractedPage], size: int | None = None, overlap: int | None = None) -> list[TextChunk]:
    config = get_settings()
    size = config.chunk_size if size is None else size
    overlap = config.chunk_overlap if overlap is None else overlap
    if not 0 <= overlap < size:
        raise ValueError("Chunk overlap must be smaller than chunk size.")
    # Keep PDF pages separate for precise citations; combine unpaginated DOCX blocks.
    if pages and all(page.page_number is None and page.sheet_name is None for page in pages):
        pages = [ExtractedPage("\n\n".join(page.text for page in pages))]
    chunks = []
    for page in pages:
        text = clean_text(page.text)
        start = 0
        while start < len(text):
            end = min(start + size, len(text))
            if end < len(text):
                end = start + split_boundary(text[start:end], max(size // 2, overlap + 1))
            content = text[start:end].strip()
            if content:
                chunks.append(TextChunk(content, len(chunks), page.page_number, page.sheet_name, page.row_number))
                if len(chunks) > config.max_document_chunks:
                    raise AppError(422, "This document creates too many chunks. Split it into smaller files.")
            if end == len(text):
                break
            start = max(start + 1, end - overlap)
    if not chunks:
        raise AppError(422, "No readable text remains after cleaning.")
    return chunks
