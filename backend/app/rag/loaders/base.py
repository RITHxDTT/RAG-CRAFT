from dataclasses import dataclass


@dataclass(frozen=True)
class ExtractedPage:
    text: str
    page_number: int | None = None
    sheet_name: str | None = None
    row_number: int | None = None
