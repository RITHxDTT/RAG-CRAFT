from pathlib import Path
from app.core.config import get_settings
from app.core.exceptions import AppError
from app.rag.loaders import xlsx_loader, docx_loader, markdown_loader, pdf_loader, text_loader
from app.rag.loaders.base import ExtractedPage

LOADERS = {"XLSX": xlsx_loader.load, "PDF": pdf_loader.load, "DOCX": docx_loader.load, "TXT": text_loader.load, "MD": markdown_loader.load}


def extract(path: Path, file_type: str) -> list[ExtractedPage]:
    pages = []
    total = 0
    try:
        for page in LOADERS[file_type](path):
            total += len(page.text)
            if total > get_settings().max_extracted_chars:
                raise AppError(422, "The extracted document is too large. Split it into smaller files.")
            pages.append(page)
    except AppError:
        raise
    except Exception:
        raise AppError(422, "Text extraction failed. The file may be damaged, encrypted, or missing.") from None
    if not any(page.text.strip() for page in pages):
        raise AppError(422, "No readable text was found. Scanned PDFs need OCR before upload.")
    return pages
