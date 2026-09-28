# Phases 2–5

## Phase 2 — Database foundation

Fourteen normalized tables are defined in `backend/app/models/`. They use UUID primary keys, timestamps, foreign keys, ownership indexes, and uniqueness/check constraints. Document and conversation organization ownership is derived through the parent chatbot. Citation names/pages are historical snapshots so future re-indexing cannot erase the citation display.

`backend/migrations/versions/43dab0e7e4d1_initial_fourteen_table_foundation.py` is an explicit, frozen Alembic migration. It does not import changing application models.

Run from the project root:

```sh
.venv/bin/python -m alembic -c backend/alembic.ini upgrade head
.venv/bin/python -m alembic -c backend/alembic.ini check
.venv/bin/python -m pytest backend/tests/test_schema.py -q
```

Verified on the project PostgreSQL database: upgrade succeeded, no schema drift. `backend/scripts/verify_postgres.py` checks upgrade → downgrade → upgrade in a unique temporary database and runs the API tests against it.

## Phase 3 — Admin authentication

Key files: `app/services/auth_service.py`, `app/core/security.py`, `app/api/auth.py`, `app/cli.py`, and `frontend/src/features/auth/`.

Argon2 hashes passwords. Signed, expiring JWT sessions are stored in HttpOnly SameSite cookies. Requests check current account activation and ADMIN organization membership. Logout invalidates all sessions for that user. Mutations require `X-Requested-With: RAGCraft`; browser origins must match `CORS_ORIGINS`. HTTPS deployments must set `COOKIE_SECURE=true`.

Create an admin (password is prompted without echo):

```sh
PYTHONPATH=backend .venv/bin/python -m app.cli create-admin --email admin@example.com
```

Each CLI-created admin receives a default organization; duplicate accounts are rejected. For unattended setup, the CLI accepts `ADMIN_PASSWORD` from the process environment, never a password argument. There is no public registration endpoint.

Test with `python -m pytest backend/tests/test_auth.py -q`. The initial 13-test suite passes. The frontend lint, TypeScript checks, and production build pass using the official Node 22 Docker image, without invoking the modified host npm installation.

## Phase 4 — Chatbot management

Key files: `app/api/chatbots.py`, `app/services/chatbot_service.py`, `app/repositories/chatbot_repository.py`, and `frontend/src/features/chatbots/`.

Admins can create, list, edit, and delete chatbots. Settings are persisted separately. Ownership is taken only from the authenticated organization; foreign chatbot IDs return 404 on reads and mutations. Dashboard counts are organization-scoped. The frontend has navigation, overview, settings, empty/error states, and a delete confirmation.

Run `python -m pytest backend/tests/test_chatbots.py -q`. All 16 tests pass on both SQLite and disposable PostgreSQL. After login, create “HR Assistant”, change its name/settings, then delete it to verify the UI. Knowledge and Playground tabs honestly show their availability until their respective phases are implemented.

## Phase 5 — Knowledge Base and uploads

Key files: `app/api/knowledge.py`, `app/services/document_service.py`, `file_validation.py`, `file_storage.py`, `app/repositories/document_repository.py`, and `frontend/src/features/knowledge/`.

PDF/DOCX/TXT/MD uploads validate extension, practical MIME type, empty contents, size, UTF-8 text/binary content, and PDF/DOCX structure. Compressed DOCX expansion is bounded. Total multipart request size is limited before unbounded disk spooling. Authentication is resolved before multipart parsing. Browser-selected filenames are reduced to safe display names; storage uses generated keys and private file permissions.

Accepted files create a DOCUMENT knowledge source, document, and queued ingestion job. SHA-256 duplicate detection is scoped to the chatbot and backed by a database unique constraint. Upload/deletion/requeue operations lock their parent chatbot to serialize conflicting mutations. Database failures clean up newly uploaded bytes; ordinary delete failures restore staged bytes. Full crash recovery and Qdrant consistency are later-phase work.

Document routes enforce both organization and chatbot access. Downloads are attachments with `nosniff` and `no-store`. The UI supports upload, status/error display, details/history, authenticated download, retry/re-index, and confirmed deletion. Dashboard activity and counts stay organization-scoped.

**Scope boundary:** no ingestion worker exists in Phase 5. Uploads stay QUEUED; the UI explicitly explains this. Retry/re-index create a new queued job. Tests simulate READY/FAILED/PROCESSING statuses to verify allowed transitions. Playground is a clearly marked future feature, not a simulated answer screen.

Run `python -m pytest backend/tests/test_documents.py -q` for focused tests. All 41 tests pass against PostgreSQL, including the storage-collision regression. The focused 25-test document suite also passes on SQLite. Frontend lint, TypeScript, and production build pass in clean Docker. Interactive browser testing could not run because the Browser runtime reported no connected browsers.

The root README includes migration, initial-admin, local/server/container startup, and full verification commands.

## Live verification

The running FastAPI and Next.js services passed `backend/scripts/smoke_test.py`: unauthenticated rejection, login/profile, chatbot create/edit/delete, document upload/download, duplicate rejection, wrong-chatbot denial, dashboard counts, logout, and frontend HTTP delivery. The script removed its temporary account, organization, audit rows, and uploaded files. No permanent admin password was generated or stored; create your admin with the documented hidden prompt.

Final packaging checks: both frontend and backend Docker images build successfully. The backend container connects to PostgreSQL and `alembic check` reports no schema drift. The final frontend image passes lint, TypeScript, and production build, and is running at localhost:3000. The updated local API is running at localhost:8000.
