> **Version 2:** Multi-user ownership, registration/profile/reset, admin catalogs, XLSX, conversations, public sharing, widgets, Telegram, source viewing, and analytics are implemented. See [the V2 phase reports and runbook](docs/v2-implementation.md) for migration/setup commands, verification results, and remaining live checks. The V1 instructions below still describe the underlying local services.

# RAG Craft — Version 1

An admin workspace for document-grounded assistants: sign in → create a chatbot → upload knowledge → wait for indexing → ask questions → inspect citations and saved conversations.

The implementation covers phases 1–13 of `setting.txt`. The automated acceptance workflow passes against real PostgreSQL, Qdrant, and local Ollama. Interactive browser verification is still pending because no browser was available in this environment.

## Architecture

```text
Next.js / TypeScript → FastAPI → PostgreSQL
                          │          ↑
                          │     ingestion worker → private files
                          │          ↓
                          └──── Qdrant + local Ollama
```

PostgreSQL owns accounts, chatbot settings, documents, jobs, chunks, and conversations. A separate worker extracts PDF/DOCX/TXT/MD, creates overlapping chunks, embeds them with `nomic-embed-text`, and indexes Qdrant. Generation uses `llama3.2:3b`. Searches require organization, chatbot, READY document, and embedding-model filters, followed by SQL ownership checks. The browser accesses only FastAPI.

## Requirements and configuration

- Python 3.13+ (local verification used Python 3.14)
- Docker Desktop / Docker Compose
- Ollama with sufficient memory for both local models
- A trusted Node 22+ installation if running the frontend outside Docker

From the project root, copy examples **only when the destination does not already exist**:

```sh
cp .env.example .env
cp frontend/.env.example frontend/.env.local
```

Set a strong `POSTGRES_PASSWORD`, match it in `DATABASE_URL`, and set a random `JWT_SECRET` of at least 32 characters. This workspace already has local configuration; preserve it. Never commit local environment files.

The example uses PostgreSQL on **localhost:55432**. Change `POSTGRES_PORT` and the `DATABASE_URL` port together. Use `localhost` consistently for the browser and API so cookie hostnames match. `COOKIE_SECURE=false` is for local HTTP; HTTPS deployments require `true`. `CORS_ORIGINS` is a JSON array of exact frontend origins.

## Start infrastructure and local models

```sh
docker compose up -d postgres qdrant
ollama serve                    # only if Ollama is not already running
```

In another terminal, install the configured models if needed:

```sh
ollama pull llama3.2:3b
ollama pull nomic-embed-text
```

PostgreSQL and Qdrant bind to localhost and retain data in named volumes. `docker compose down` preserves those volumes. Generation rejects Ollama cloud/remote models.

## Install and migrate the backend

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r backend/requirements.lock.txt
.venv/bin/python -m alembic -c backend/alembic.ini upgrade head
.venv/bin/python -m alembic -c backend/alembic.ini check
```

Migrations create the 14 foundation tables plus one internal durable vector-cleanup table. They do not create an admin automatically. Existing records are preserved by the additive migrations.

## Create your admin

```sh
PYTHONPATH=backend .venv/bin/python -m app.cli create-admin --email admin@example.com
```

Enter your chosen password of at least 12 characters at the hidden prompt. **There is no default password, and arbitrary passwords will not log in.** Sign in with the password used when the account was created. Existing accounts and passwords are never overwritten by this command or the acceptance tests.

For unattended setup, supply `ADMIN_PASSWORD` through the process environment. Do not put passwords in command arguments or commit them. Passwords are stored as Argon2 hashes; sessions use HttpOnly cookies.

## Run the API, worker, and frontend

Run the API in one terminal:

```sh
.venv/bin/python -m uvicorn app.main:app --app-dir backend --reload --reload-dir backend/app --port 8000
```

Run the worker in a second terminal:

```sh
PYTHONPATH=backend .venv/bin/python -m app.worker
```

Keep both running. Start only one worker per database; a PostgreSQL advisory lock enforces this. Restart the worker after changing its code or configuration. Interrupted jobs return to the queue on worker restart.

Build and start the frontend:

```sh
docker compose --profile ui up -d --build frontend
```

Open **http://localhost:3000**. API documentation is at **http://localhost:8000/docs**. `/health` reports API liveness; `/health/ready` checks PostgreSQL, Qdrant, the worker, Ollama, and both configured local models, returning 503 when a dependency is unavailable.

A clean Docker build is the verified frontend path on this machine. The host npm launcher was previously found to contain unexpected obfuscated code and was not used; see [the original environment record](docs/phase-1.md). With a trusted local Node/npm installation, `cd frontend`, `npm ci`, and `npm run dev` are also supported. Rebuild the frontend after changing `NEXT_PUBLIC_API_URL` because it is embedded at build time.

## Use the workspace

1. Sign in and create **HR Assistant**.
2. Open **Knowledge Base → Add Knowledge** and upload a readable PDF, DOCX, UTF-8 TXT, or MD file.
3. Watch `QUEUED → PROCESSING → READY`. If processing fails, inspect the error, address its cause, and click **Retry**.
4. Open **Playground** and ask a question answered in that document.
5. Expand the source cards to see excerpts, PDF page numbers, and authenticated original downloads.
6. Ask follow-up questions, start a new chat, or select an existing conversation. Messages and citation snapshots survive refreshes.
7. Use **Re-index** to regenerate a ready document's chunks and vectors. Delete documents or chatbots through the confirmation action when no document is processing.

Another chatbot cannot retrieve these documents. Unsupported questions return an insufficient-knowledge answer. Deleted or half-indexed documents are excluded immediately through SQL readiness checks; durable cleanup jobs remove their Qdrant vectors and retry outages automatically. Old conversation excerpts remain as historical snapshots after a document is deleted; its original download then returns 404.

## Configuration and limits

The defaults are recorded in `.env.example`:

- `CHUNK_SIZE=800`, `CHUNK_OVERLAP=100`, `TOP_K=5`
- `RETRIEVAL_SCORE_THRESHOLD=0.3`, `MAX_CONTEXT_CHARS=16000`
- `MAX_UPLOAD_SIZE_MB=20`, `MAX_EXTRACTED_CHARS=4000000`, `MAX_DOCUMENT_CHUNKS=10000`
- `EMBEDDING_BATCH_SIZE=16`, `OLLAMA_TIMEOUT_SECONDS=180`, `QDRANT_TIMEOUT_SECONDS=30`
- `WORKER_POLL_SECONDS=2`

Local document files live in `backend/storage/` unless `STORAGE_DIR` is set. Back up the database and document storage together. Changing the embedding model requires re-indexing; a different vector dimension requires a new `QDRANT_COLLECTION` as well. Chunk/context limits count characters, not model tokens.

V1 has no OCR: scanned or empty PDFs fail with an explanation. DOCX/TXT/MD citations have excerpts but no invented page numbers. The small local model can still make mistakes; inspect cited excerpts. This prototype uses one ingestion worker and one API process with bounded generation and login throttling; it is not a distributed production deployment. Filesystem deletion staging can restore ordinary transaction failures, but a machine crash may require reconciling `.trash-*` files against PostgreSQL. V2 adds Telegram, widgets, and public links; see the V2 runbook. Crawling, billing, and other V3 exclusions remain outside this implementation.

## Tests and acceptance

```sh
# Fast isolated suite
.venv/bin/python -m pytest -q

# Real PostgreSQL suite and migration upgrade/downgrade/reapply
.venv/bin/python backend/scripts/verify_postgres.py

# Complete live workflow: API, worker, frontend, Qdrant, and Ollama must be running
.venv/bin/python backend/scripts/acceptance_test.py

# Lint, TypeScript, and production build in trusted Docker images
docker build -t rag-craft-frontend:v1 frontend
docker build -t rag-craft-backend:v1 backend
```

Verified: **69 tests pass on SQLite and PostgreSQL**, migration round trips pass, and **9 live acceptance groups pass**. The real model answers “18 days” from a synthetic handbook with a citation to page 12. Acceptance also checks follow-up history, tenant/chatbot isolation, unsupported questions, retry failures, duplicate-free re-indexing, document/vector deletion, and logout. Tests create disposable accounts and synthetic documents; existing accounts and documents are not changed. Vector cleanup remains durable if Qdrant is temporarily unavailable.

The PostgreSQL script requires create/drop database privileges and deletes only its uniquely named test database. The acceptance script writes [its latest result](docs/acceptance-results.json). Implementation notes are in [phases 2–5](docs/phases-2-5.md) and [phases 6–13](docs/phases-6-13.md). HTTP and build checks passed; interactive browser testing remains unverified.

## Optional container backend and worker

Stop the local API and worker before using this alternative:

```sh
docker compose --profile api --profile ui up -d --build
# For a new installation only:
docker compose --profile api exec backend python -m app.cli create-admin --email admin@example.com
```

The API migrates before startup; its liveness healthcheck gates worker startup. API and worker share the `document_storage` volume, which is **separate from local `backend/storage/`**. Use one mode consistently; switching an existing installation requires moving document files too. Docker Desktop's `host.docker.internal` points to host Ollama. Ollama must be reachable from containers. The currently verified local setup runs the API and worker on the host with the frontend and databases in Docker.

The root repository currently tracks `frontend/` as a separate Git repository (gitlink). That existing layout and both repositories' history have been preserved.
