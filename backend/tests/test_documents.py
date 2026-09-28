from io import BytesIO
from uuid import UUID
from zipfile import ZipFile
import pytest
from docx import Document as DocxDocument
from pypdf import PdfWriter
from sqlalchemy import func, select
from sqlalchemy.exc import SQLAlchemyError
from app.core.config import get_settings
from app.models import Document, IngestionJob, KnowledgeSource
from app.schemas.auth import AdminCreate
from app.services.auth_service import create_admin
from app.services.file_storage import storage_path
from conftest import PASSWORD


@pytest.fixture
def bot_id(logged_in):
    return logged_in.post("/api/chatbots", json={"name": "HR Assistant"}).json()["id"]


def upload(client, bot_id, name="policy.txt", body=b"Employees receive 18 days of annual leave.", mime="text/plain"):
    return client.post(f"/api/chatbots/{bot_id}/documents", files={"file": (name, body, mime)})


def pdf_bytes():
    output = BytesIO()
    writer = PdfWriter()
    writer.add_blank_page(width=100, height=100)
    writer.write(output)
    return output.getvalue()


def docx_bytes():
    output = BytesIO()
    document = DocxDocument()
    document.add_paragraph("Employees receive 18 days of annual leave.")
    document.save(output)
    return output.getvalue()


@pytest.mark.parametrize("name,body,mime", [
    ("policy.txt", b"Annual leave: 18 days", "text/plain"),
    ("policy.md", b"# Annual leave\n18 days", "text/markdown"),
    ("policy.pdf", pdf_bytes(), "application/pdf"),
    ("policy.docx", docx_bytes(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
])
def test_supported_uploads_are_queued_with_jobs(logged_in, db, bot_id, name, body, mime):
    response = upload(logged_in, bot_id, name, body, mime)
    assert response.status_code == 201, response.text
    result = response.json()
    assert result["status"] == "QUEUED"
    assert "storage_key" not in result
    doc = db.get(Document, UUID(result["id"]))
    assert storage_path(doc.storage_key).read_bytes() == body
    assert storage_path(doc.storage_key).stat().st_mode & 0o077 == 0
    path = f'/api/chatbots/{bot_id}/documents/{result["id"]}'
    assert logged_in.get(path).json()["jobs"][0]["status"] == "QUEUED"
    download = logged_in.get(path + "/download")
    assert download.content == body
    assert "attachment" in download.headers["content-disposition"]
    assert download.headers["x-content-type-options"] == "nosniff"
    assert logged_in.get("/api/dashboard").json()["total_documents"] == 1
    assert logged_in.get("/api/knowledge/recent").json()[0]["chatbot_id"] == bot_id


@pytest.mark.parametrize("name,body,mime,status", [
    ("bad.exe", b"data", "application/octet-stream", 415),
    ("empty.txt", b"", "text/plain", 422),
    ("blank.md", b"  \n\t", "text/markdown", 422),
    ("bad.txt", b"\xff\xfe\x00", "text/plain", 422),
    ("binary.txt", b"hello\x00world", "text/plain", 422),
    ("fake.pdf", b"not a PDF", "application/pdf", 422),
    ("truncated.pdf", b"%PDF-1.7\ntruncated", "application/pdf", 422),
    ("broken.docx", b"not a docx", "application/zip", 422),
    ("wrong.txt", b"hello", "application/pdf", 415),
])
def test_invalid_uploads_leave_no_records(logged_in, db, bot_id, name, body, mime, status):
    response = upload(logged_in, bot_id, name, body, mime)
    assert response.status_code == status, response.text
    assert db.scalar(select(func.count(Document.id))) == 0
    assert db.scalar(select(func.count(KnowledgeSource.id))) == 0


def test_oversized_upload(logged_in, db, bot_id, monkeypatch):
    monkeypatch.setattr(get_settings(), "max_upload_size_mb", 1)
    assert upload(logged_in, bot_id, body=b"x" * (1024 * 1024 + 1)).status_code == 413
    assert logged_in.get("/api/knowledge/config").json()["max_upload_size_mb"] == 1
    assert db.scalar(select(func.count(Document.id))) == 0


def test_total_request_limit(logged_in, bot_id):
    # Covers the middleware's body bound, not just the post-parse per-file check.
    response = upload(logged_in, bot_id, body=b"x" * (21 * 1024 * 1024))
    assert response.status_code == 413


def test_duplicate_content_is_scoped_per_chatbot(logged_in, bot_id):
    assert upload(logged_in, bot_id).status_code == 201
    assert upload(logged_in, bot_id, name="renamed.txt").status_code == 409
    other = logged_in.post("/api/chatbots", json={"name": "IT Assistant"}).json()["id"]
    assert upload(logged_in, other).status_code == 201
    assert len(logged_in.get(f"/api/chatbots/{bot_id}/documents").json()) == 1


def test_document_access_is_scoped_by_organization_and_chatbot(logged_in, db, bot_id):
    doc_id = upload(logged_in, bot_id).json()["id"]
    other_bot = logged_in.post("/api/chatbots", json={"name": "IT Assistant"}).json()["id"]
    wrong_path = f"/api/chatbots/{other_bot}/documents/{doc_id}"
    assert logged_in.get(wrong_path).status_code == 404
    assert logged_in.get(wrong_path + "/download").status_code == 404
    assert logged_in.delete(wrong_path).status_code == 404
    create_admin(db, AdminCreate(email="other@example.com", password=PASSWORD))
    logged_in.post("/api/auth/login", json={"email": "other@example.com", "password": PASSWORD})
    base = f"/api/chatbots/{bot_id}/documents"
    path = base + f"/{doc_id}"
    for endpoint in [base, path, path + "/download"]:
        assert logged_in.get(endpoint).status_code == 404
    assert upload(logged_in, bot_id).status_code == 404
    assert logged_in.delete(path).status_code == 404
    assert logged_in.post(path + "/retry").status_code == 404
    assert logged_in.post(path + "/reindex").status_code == 404
    assert logged_in.get("/api/knowledge/recent").json() == []


def test_retry_reindex_and_errors(logged_in, db, bot_id):
    doc_id = upload(logged_in, bot_id).json()["id"]
    path = f"/api/chatbots/{bot_id}/documents/{doc_id}"
    assert logged_in.post(path + "/retry").status_code == 409
    assert logged_in.post(path + "/reindex").status_code == 409
    doc = db.get(Document, UUID(doc_id))
    doc.status, doc.error_message = "FAILED", "Embedding service unavailable."
    job = db.scalar(select(IngestionJob))
    job.status, job.error_message = "FAILED", doc.error_message
    db.commit()
    assert logged_in.get(path).json()["error_message"] == "Embedding service unavailable."
    response = logged_in.post(path + "/retry")
    assert response.json()["status"] == "QUEUED"
    assert response.json()["error_message"] is None
    assert len(logged_in.get(path).json()["jobs"]) == 2
    doc.status = "READY"
    db.commit()
    assert logged_in.post(path + "/reindex").json()["status"] == "QUEUED"
    doc.status = "PROCESSING"
    db.commit()
    assert logged_in.delete(path).status_code == 409
    assert logged_in.delete(f"/api/chatbots/{bot_id}").status_code == 409


def test_delete_removes_file_source_and_jobs(logged_in, db, bot_id):
    doc_id = upload(logged_in, bot_id, name="../../policy.txt").json()["id"]
    doc = db.get(Document, UUID(doc_id))
    assert doc.name == "policy.txt"
    path = storage_path(doc.storage_key)
    assert logged_in.delete(f"/api/chatbots/{bot_id}/documents/{doc_id}").status_code == 204
    assert not path.exists()
    for model in [Document, KnowledgeSource, IngestionJob]:
        assert db.scalar(select(func.count(model.id))) == 0
    assert upload(logged_in, bot_id).status_code == 201


def test_chatbot_deletion_cleans_document_files(logged_in, db, bot_id):
    upload(logged_in, bot_id)
    path = storage_path(db.scalar(select(Document)).storage_key)
    assert logged_in.delete(f"/api/chatbots/{bot_id}").status_code == 204
    assert not path.exists()
    assert db.scalar(select(func.count(Document.id))) == 0


def test_upload_database_failure_cleans_saved_file(logged_in, db, bot_id, monkeypatch):
    def fail():
        raise SQLAlchemyError("do not expose this exception")
    monkeypatch.setattr(db, "commit", fail)
    response = upload(logged_in, bot_id)
    assert response.status_code == 503
    assert "do not expose" not in response.text
    assert list(get_settings().storage_dir.iterdir()) == []
    assert db.scalar(select(func.count(Document.id))) == 0


def test_delete_database_failure_restores_file(logged_in, db, bot_id, monkeypatch):
    doc_id = upload(logged_in, bot_id).json()["id"]
    path = storage_path(db.get(Document, UUID(doc_id)).storage_key)
    def fail():
        raise SQLAlchemyError("commit failed")
    monkeypatch.setattr(db, "commit", fail)
    response = logged_in.delete(f"/api/chatbots/{bot_id}/documents/{doc_id}")
    assert response.status_code == 503
    assert path.is_file()
    assert db.scalar(select(func.count(Document.id))) == 1


def test_compressed_docx_expansion_limit(logged_in, bot_id, monkeypatch):
    monkeypatch.setattr(get_settings(), "max_upload_size_mb", 1)
    archive = BytesIO()
    with ZipFile(archive, "w", compression=8) as target:
        target.writestr("word/document.xml", b"x" * (6 * 1024 * 1024))
    response = upload(logged_in, bot_id, "oversized.docx", archive.getvalue(), "application/zip")
    assert response.status_code == 422
    assert "expands" in response.json()["detail"]


def test_multiple_files_and_missing_file_are_rejected(logged_in, bot_id):
    path = f"/api/chatbots/{bot_id}/documents"
    response = logged_in.post(path, files=[("file", ("a.txt", b"one")), ("file", ("b.txt", b"two"))])
    assert response.status_code == 400
    assert logged_in.post(path).status_code == 422


def test_storage_collision_never_removes_existing_file(tmp_path, monkeypatch):
    from app.core.exceptions import AppError
    from app.services.file_storage import save_file
    monkeypatch.setattr(get_settings(), "storage_dir", tmp_path)
    original = tmp_path / "existing.txt"
    original.write_bytes(b"keep this existing document")
    with pytest.raises(AppError):
        save_file("existing.txt", b"new upload")
    assert original.read_bytes() == b"keep this existing document"
