# RAG Craft — Version 1

RAG Craft is an admin workspace for document-grounded assistants. The target workflow is login → create chatbot → upload knowledge → process/index → ask a question → receive an answer with sources.

**Implemented through Phase 5:** the database foundation, admin authentication, chatbot CRUD/settings, dashboard, and document management. Uploads create persisted `QUEUED` documents and ingestion jobs. **Text extraction, embedding, retrieval, answers, and citations are not implemented yet** (phases 6–11). The Playground explains that it is not available yet.

## Architecture

```text
Next.js / TypeScript / Tailwind
              ↓ REST API + HttpOnly session cookie
FastAPI → services → repositories → PostgreSQL
              ↓ (later phases)
         Qdrant + local Ollama
```

Only FastAPI accesses storage and infrastructure. All chatbot/document queries are scoped to the authenticated organization; document routes additionally require the correct chatbot. PostgreSQL owns application data. Qdrant will hold vectors scoped by organization and chatbot. Model defaults are `llama3.2:3b` for generation and `nomic-embed-text` for embeddings.

## Requirements

- Python 3.13+ (local tests use Python 3.14)
- Docker Desktop / Docker Compose
- Node 22+ with a trusted npm installation if running Next.js outside Docker
- Ollama for the later RAG phases; health checks can report an existing local service

## Configuration

From the project root:

```sh
cp .env.example .env              # only if .env does not already exist
cp frontend/.env.example frontend/.env.local
```

Set a strong `POSTGRES_PASSWORD`, match it in `DATABASE_URL`, and set a random `JWT_SECRET` of at least 32 characters. Local environment files are ignored. This workspace already has generated local configuration; do not overwrite it.

PostgreSQL is exposed on **localhost:55432**, avoiding existing databases on ports 5432 and 5433. Set `POSTGRES_PORT` and the `DATABASE_URL` port together. The application can switch to external PostgreSQL by changing `DATABASE_URL`.

`MAX_UPLOAD_SIZE_MB` defaults to 20. Files are stored privately in `backend/storage/` unless `STORAGE_DIR` is set. Back up database records and file storage together. `COOKIE_SECURE=false` is for localhost HTTP; use `true` with HTTPS. `CORS_ORIGINS` is a JSON array of exact frontend origins. Use `localhost` consistently for the UI and API so cookie hostnames match.

## Start PostgreSQL and Qdrant

```sh
docker compose up -d postgres qdrant
```

These services bind only to localhost and retain data in named volumes. `docker compose down` stops them without deleting their volumes.

## Install backend dependencies and migrate

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r backend/requirements.lock.txt
.venv/bin/python -m alembic -c backend/alembic.ini upgrade head
.venv/bin/python -m alembic -c backend/alembic.ini check
```

`requirements.txt` documents direct dependency ranges; `requirements.lock.txt` records the tested versions. Alembic creates all 14 required tables. It does not create an admin automatically.

## Create your admin

```sh
PYTHONPATH=backend .venv/bin/python -m app.cli create-admin --email admin@example.com
```

Enter a password of at least 12 characters at the hidden prompt. The command creates the user, a default organization, and ADMIN membership in one transaction. Passwords are stored as Argon2 hashes. There is no default password or public registration page.

For unattended setup, pass `ADMIN_PASSWORD` through the process environment; do not put passwords in command arguments or commit them. Existing accounts are never overwritten.

## Run FastAPI

```sh
.venv/bin/python -m uvicorn app.main:app --app-dir backend --reload --reload-dir backend/app --port 8000
```

- API documentation: http://localhost:8000/docs
- Liveness: http://localhost:8000/health
- Dependencies: http://localhost:8000/health/ready (503 when any dependency is unavailable)

## Run the frontend

A clean Docker build is the verified path on this machine because the global host npm launcher was found to contain unexpected obfuscated code. It has not been repaired or used for these phases. See `docs/phase-1.md` for the original finding.

```sh
docker compose --profile ui up -d --build frontend
```

Open **http://localhost:3000**. Keep the local FastAPI process running. The public API URL is a frontend build argument, so rebuild the frontend after changing `NEXT_PUBLIC_API_URL`.

With a trusted local Node/npm installation, development can instead use:

```sh
cd frontend
npm ci
npm run dev
```

The existing frontend project and package versions are retained.

## Try phases 3–5

1. Create an admin using the command above and sign in.
2. Click **Create chatbot**, name it **HR Assistant**, and open it.
3. Use **Settings** to edit its description and response settings.
4. Open **Knowledge Base → Add Knowledge** and upload PDF, DOCX, UTF-8 TXT, or MD.
5. Confirm the document is `QUEUED`. **View** shows metadata/history and an authenticated download of the original.
6. Upload the same contents again; the API rejects the duplicate for that chatbot. Another chatbot may upload the same content independently.
7. Delete a document or chatbot using the confirmation action. Records and stored files are removed.
8. Sign out. All sessions for that admin are invalidated.

Retry is available only for failed documents; re-index is available for ready documents. Both create a new queued job. In this phase, no worker runs these jobs, and the UI says so. PDF/DOCX parsing currently validates readability; full text extraction and OCR handling belong to later work.

## Tests and verification

```sh
# Fast isolated suite (SQLite with foreign-key enforcement)
.venv/bin/python -m pytest -q

# Real PostgreSQL suite + upgrade/downgrade/upgrade, using a new disposable database
.venv/bin/python backend/scripts/verify_postgres.py

# Live HTTP workflow, with API and frontend running
.venv/bin/python backend/scripts/smoke_test.py

# Frontend lint, TypeScript, and production build using trusted Node in Docker
docker build -t rag-craft-frontend:phase5 frontend
```

The PostgreSQL verification script requires permission to create/drop a database. It creates a unique `ragcraft_test_*` database and deletes only that database. The live smoke script creates a temporary admin/organization, exercises the workflow, and cleans up its records/files. Neither script uses or changes an existing admin account.

Verified: 41 tests pass against PostgreSQL, migration upgrade/rollback/reapply succeeds, the live HTTP workflow passes, and both Docker images build. Frontend lint and TypeScript checks pass.

Tests cover authentication/session invalidation, tenant-scoped CRUD and documents, file types/corruption/size limits, duplicates, queued jobs, retry/re-index transitions, download authorization, and storage cleanup on database failures. Detailed phase records are in `docs/phases-2-5.md`. Interactive browser verification was unavailable because no browser was connected.

## Optional backend container

The backend Dockerfile includes locked dependencies and migrations. The optional Compose `api` profile runs migrations before starting FastAPI:

```sh
# Stop the local FastAPI process first to free port 8000.
docker compose --profile api up -d --build backend
# Create an admin interactively inside that backend:
docker compose --profile api exec backend python -m app.cli create-admin --email admin@example.com
```

The backend container uses the `document_storage` volume, separate from local `backend/storage`. Use one backend mode consistently; switching modes requires moving document files too. Docker Desktop's `host.docker.internal` points to host Ollama. The application itself has no Docker-specific database logic.

## Later phases

When working on embeddings/generation:

```sh
ollama serve
ollama pull llama3.2:3b
ollama pull nomic-embed-text
```

These model operations are not part of phases 2–5. Queue consumption, restart recovery, cross-store indexing/deletion, retrieval isolation, generation, citations, and conversation APIs remain pending. Filesystem staging can restore a deletion after an ordinary database failure, but is not a crash-safe distributed transaction; reconcile private `.trash-*` files with PostgreSQL after a process/machine crash.

The original Git repository is still inside `frontend/`. Its history was preserved; root repository consolidation remains separate pending setup work.

## References

- [FastAPI JWT and password hashing](https://fastapi.tiangolo.com/tutorial/security/oauth2-jwt/)
- [Alembic migrations](https://alembic.sqlalchemy.org/en/latest/tutorial.html)
- [Starlette multipart request limits](https://www.starlette.io/requests/)
