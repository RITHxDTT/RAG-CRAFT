from pathlib import Path
from docx import Document
from docx.table import Table
from app.rag.loaders.base import ExtractedPage


def load(path: Path):
    # DOCX has no reliable physical page numbers without a layout engine.
    for block in Document(path).iter_inner_content():
        if isinstance(block, Table):
            yield ExtractedPage("\n".join("\t".join(cell.text for cell in row.cells) for row in block.rows))
        else:
            yield ExtractedPage(block.text)
