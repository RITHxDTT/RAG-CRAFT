# Version 1 completion record

## Phase 6 — Extraction and chunks

`app/rag/loaders/`, `cleaner.py`, and `chunker.py` implement PDF page extraction, ordered DOCX paragraphs/tables, UTF-8 TXT/MD, conservative cleaning, and recursive boundary selection with overlap. Chunk size/overlap and document limits are configurable. Scanned PDFs fail with an OCR explanation; this version does not implement OCR.

Check: `.venv/bin/python -m pytest backend/tests/test_extraction.py -q` (7 passed).

## Phase 7 — Durable ingestion and vectors

`app/worker.py` runs a single database-backed worker, protected by a PostgreSQL advisory lock. Jobs transition QUEUED → PROCESSING → READY/FAILED. Interrupted processing is requeued at worker restart. `app/services/ingestion_service.py` writes vectors before marking PostgreSQL records READY. Re-indexing replaces old vectors/chunks. Incomplete vectors are excluded from retrieval because only PostgreSQL READY document IDs can be searched.

`app/rag/embedding_service.py` calls local Ollama's dedicated embedding endpoint. `vector_store.py` uses Qdrant's REST API, avoiding another major dependency. Embedding dimensions are checked and ownership fields have payload indexes. Model changes require re-indexing; vector searches require the configured embedding-model payload value.

The migration adds durable vector cleanup jobs and citation snapshot fields. API deletion commits a cleanup task with the database deletion. Until the worker removes Qdrant points, SQL readiness/ownership validation excludes them. Cleanup retries Qdrant outages automatically. The original 14 foundation tables remain, with one additional internal cleanup table.

Checks: ingestion unit tests pass; real `nomic-embed-text` returned 768-dimensional embeddings and Qdrant insertion/search/deletion passed with temporary vectors. A different chatbot's filtered search returned no points.

Run the worker from the project root: `PYTHONPATH=backend .venv/bin/python -m app.worker`.

## Phase 8 — Retrieval

`app/rag/retriever.py` finds READY document IDs in PostgreSQL, embeds the question, and searches Qdrant with organization AND chatbot AND ready-document AND embedding-model filters. Retrieved IDs are hydrated through another SQL ownership/readiness check. Qdrant payload content never substitutes for authoritative SQL content. Context size and relevance threshold are configurable.

Check: `.venv/bin/python -m pytest backend/tests/test_retrieval.py -q` (3 passed). No ready knowledge returns no context without calling Ollama.

## Phase 9 — Grounded generation

`prompt_builder.py`, `generator.py`, and `pipeline.py` separate prompt construction, local Ollama calls, and orchestration. Document content is marked as untrusted data. Structured output contains an answer, an evidence flag, and numeric source selectors. The backend accepts only selectors for retrieved chunks; unsupported/no-evidence responses become an explicit insufficient-knowledge answer. This is a grounding aid, not a proof that every generated sentence is correct. Users can inspect source excerpts.

The generator checks that the selected model is local and rejects Ollama remote/cloud models. Timeouts/unavailability return clean errors. Questions without context do not invoke generation.

Checks: generation unit tests pass; real `llama3.2:3b` answered the synthetic annual-leave question with 18 and selected the provided source.

## Phase 10 — Playground and citations

`app/api/playground.py`, `app/services/playground_service.py`, `frontend/src/features/playground/`, and `playground.service.ts` implement question submission, chat bubbles, loading/error states, and expandable source cards with page numbers, excerpts, and authenticated original downloads. The frontend never parses citations out of prose.

Check: Playground endpoint tests verify structured sources, authorization, and insufficient-knowledge behavior. Frontend production checks are run in the clean Node container.

## Phase 11 — Persistent conversations

`models/conversation.py`, `repositories/conversation_repository.py`, `services/conversation_service.py`, and `schemas/conversation.py` provide scoped history and deletion. A turn saves both messages and source snapshots in one transaction after generation succeeds. Message sequence numbers enforce ordering. The migration backfills order for existing rows. Re-indexing/deletion can remove chunk links without losing historical excerpts. Recent conversation context helps follow-up retrieval but never substitutes for current knowledge evidence.

Check: conversation tests cover ordering, snapshots, failed-generation rollback, and cross-tenant/chatbot authorization. The real acceptance flow reloads source cards and answers a follow-up question.

## Phase 12 — Errors, health, logs, and tests

Provider wrappers return clean timeout/unavailable/malformed-response errors. Generation output is validated, unknown citation selectors are discarded, and empty evidence produces a canonical insufficient-knowledge answer. A bounded per-IP login limiter and generation semaphore limit local workload. Logs identify processing stages and record IDs without passwords, file contents, questions, or model prompts. Health checks report the worker advisory lock and both local models. The Knowledge Base explains when processing is paused.

Important files: `core/rate_limit.py`, `core/worker_lock.py`, `services/health_service.py`, `rag/embedding_service.py`, `rag/generator.py`, `tests/test_provider_errors.py`.

Checks: **69 tests passed on SQLite and PostgreSQL**. `backend/scripts/verify_postgres.py` verified upgrade → downgrade → upgrade, schema agreement, and the whole suite in an isolated database. The only test warning is an upstream Starlette/httpx deprecation.

## Phase 13 — Live acceptance and runbook

`backend/scripts/acceptance_test.py` creates temporary tenants and synthetic documents, then exercises the running API, real worker, PostgreSQL, Qdrant, and local Ollama. It does not use or modify existing admin credentials. The synthetic 12-page handbook produces “18 days” and a citation to page 12 using `llama3.2:3b` and `nomic-embed-text`. All nine acceptance groups passed, including follow-ups/history, chatbot/tenant isolation, unsupported-question abstention, re-index replacement, unreadable-document retry, and vector cleanup after deletion. Temporary records/files are cleaned in a `finally` block; deletion jobs remain durable until processed.

Run: `.venv/bin/python backend/scripts/acceptance_test.py` with all services running. The latest machine-readable result is `docs/acceptance-results.json`.

`README.md` now documents installation, migrations, admin creation, both local processes, frontend startup, configuration, container alternatives, tests, and V1 limits. `docker-compose.yml` includes a worker sharing the container API's file volume, gated on migrated API startup. Existing local document storage and user accounts are preserved.

Frontend lint, TypeScript, and production build are checked through the clean Node Docker image. Interactive browser verification is still pending: the browser runtime returned no connected browsers. This prevents claiming that the click-by-click UI acceptance scenario has been observed, even though its API workflow and frontend production checks pass.
