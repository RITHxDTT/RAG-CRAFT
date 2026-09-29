# Version 2 phase reports and runbook

Updated 2026-09-29. The user authorized continuing through all phases after Phase 1. V2 extends the existing application, preserving the navy sidebar, teal accents, rounded white cards, responsive layouts, and Lucide icons.

## Run it

From the repository root:

```sh
.venv/bin/python -m pip install -r backend/requirements.lock.txt
.venv/bin/python -m alembic -c backend/alembic.ini upgrade head
.venv/bin/python -m uvicorn app.main:app --app-dir backend --reload --reload-dir backend/app --port 8000
```

Restart the worker in another terminal:

```sh
PYTHONPATH=backend .venv/bin/python -m app.worker
```

Frontend:

```sh
cd frontend
npm install
npm run dev
```

Production compilation was verified with `npm run build -- --webpack`. The host Turbopack build could not bind its CSS worker port, including the approved retry; webpack compiled successfully. The subsequent official Node Docker build also passed with Turbopack. A running Docker frontend does not automatically include source edits. For Docker UI mode rebuild with `docker compose --profile ui up -d --build frontend`; rebuild backend and worker too if using the API profile.

Verification used disposable databases. The local application database was subsequently backed up privately under `backend/storage/backups/` and migrated successfully. Apply the migration above when deploying this code to another environment. Preserve backups before a normal release migration. Ambiguous legacy chatbot ownership deliberately causes migration failure; supply verified creator audit records before retrying rather than assigning an arbitrary owner.

## Configuration and manual acceptance

New examples are in `.env.example`:

- `FRONTEND_URL`: browser origin for public links and embeds.
- `PUBLIC_API_URL`: API origin for webhook registration; public HTTPS is required for Telegram.
- `INTEGRATION_ENCRYPTION_KEY`: persistent Fernet key, configured privately. Preserve it across restarts. Changing it without re-encrypting credentials requires reconnecting Telegram.
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS`: password reset delivery. Without SMTP the UI explains that reset email is unavailable.

Existing administrators retain ADMIN. New registration always assigns USER. Use the existing README CLI command to provision administrators. Catalog entries describe locally installed Ollama models; adding an entry does not download the model.

Telegram credentials are entered in the owner's Channels screen, validated, and encrypted. Responses show only a mask. The [official Telegram protocol](https://core.telegram.org/bots/api#setwebhook) requires credentials in its own outbound HTTPS API path; they are never placed in RAG Craft public URLs, responses, or logs. Webhooks use random identifiers plus secret-header validation. V2 handles private text chats; other updates are acknowledged without processing. Retry records prevent repeated generation. A crash after Telegram accepts a message but before local commit can still repeat delivery; exactly-once transport is not claimed.

Remaining live checks:

1. Configure SMTP; request a reset, use the email link, and verify that reuse fails.
2. Configure public HTTPS and the encryption key; connect a real BotFather token, test connection, ask a question, inspect the TELEGRAM conversation, disable/disconnect.
3. In a browser, test registration, catalogs, chatbot configuration, multi-file upload, PDF page links and deleted sources; embed widget code on another origin and send a question.

No browser was available through the installed Browser tool in this session. Real Telegram/SMTP credentials were not supplied. These checks are pending, not reported as passed.

## Verification commands

```sh
.venv/bin/python -m pytest -q
.venv/bin/python backend/scripts/verify_postgres.py
.venv/bin/python backend/scripts/acceptance_v2.py
cd frontend
npm run lint
npx tsc --noEmit
npm run build -- --webpack
```

`verify_postgres.py` creates a disposable database, inserts a V1 user/chatbot, verifies ownership/role/model backfill, downgrades/upgrades, checks schema drift, runs the suite against PostgreSQL, and removes its database.

`acceptance_v2.py` creates an isolated database, Qdrant collection, and temporary uploads. It uses real Ollama/Qdrant for ingestion and generation, tests Playground/share/widget APIs, and cleans up its own resources. Results: `docs/v2-acceptance-results.json`. It sends no Telegram messages or SMTP mail.

## Phase checkpoints

Paths abbreviated as `app/...` below are under `backend/`; frontend paths are explicit. Each phase uses the common run commands above and applies migrations through Alembic. Closely related model/prompt and conversation/channel schema additions share migrations rather than duplicating tables.

### 1. Inspect V1

Created `docs/v2-phase-1.md`: architecture, schema, authentication, isolation gaps, RAG and visual style inventory. No API/database changes. Baseline: 69 passing backend tests and zero lint errors.

### 2. Roles and ownership

Implemented server-derived individual ownership, separate authenticated-user/admin dependencies, explicit legacy role/owner backfill, and owner checks on direct/nested resources and aggregates.

Created: `migrations/versions/a201_v2_ownership.py`, `tests/test_v2_ownership.py`.

Modified: identity/chatbot models, auth schema/service, dependencies and authenticated route files, chatbot/document/conversation services, user/chatbot/document repositories.

Database: `users.role`, `users.full_name`, indexed `chatbots.owner_id` with foreign key. API: existing resource routes accept authenticated owners; cross-organization resources remain 404 and same-organization foreign ownership is 403. Verify: V2 ownership tests cover shared-organization isolation, downloads, conversations, channel management, and summaries.

### 3. Registration, profile, reset

Implemented USER registration, login/logout reuse, full-name editing, hashed single-use reset tokens, SMTP, expiry/session revocation, and modern account screens. Significant password whitespace is preserved.

Created: `app/services/password_service.py`, `migrations/versions/a202_password_reset.py`, `tests/test_v2_auth.py`, `frontend/src/features/profile/profile.tsx`, `frontend/src/app/reset-password/page.tsx`.

Modified: identity/model exports, auth API/schema/service, configuration, frontend auth types/service/login/session shell/workspace.

Database: `password_resets` with indexed user, unique hash and expiration. APIs: `/api/auth/register`, PATCH `/api/auth/me`, `/forgot-password`, `/reset-password`. Verify: registration, duplicate email, role injection, profile updates, disabled users, reset reuse and session revocation tests. Live SMTP check described above.

### 4. User chatbot CRUD

Reused existing CRUD and modern forms with owner enforcement and role-aware navigation. Status changes affect every channel; disabling requires frontend confirmation. No duplicate CRUD service or additional migration.

Modified: existing chatbot service/repository/routes, workspace and chatbot form. APIs retain existing paths. Verify: original chatbot CRUD tests, USER registration creating chatbots, and V2 ownership tests.

### 5. Model catalog

Implemented ADMIN create/list/edit/enable/disable/default and backend-loaded user selection. Generation checks enabled catalog status. Model identifiers are resolved outside RAG.

Created: `app/models/catalog.py`, `app/schemas/catalog.py`, `app/repositories/catalog_repository.py`, `app/services/catalog_service.py`, `app/api/catalog.py`, `migrations/versions/a203_catalog.py`, `tests/test_v2_catalog.py`, `frontend/src/services/catalog.service.ts`, `frontend/src/features/admin/catalog.tsx`.

Modified: model exports, chatbot model/schema/service, API registration, catalog test fixture, frontend chatbot types/form/navigation.

Database: models, unique default index, provider/default checks, chatbot model reference and legacy backfill. APIs: `/api/models`, `/api/admin/models`, PATCH by ID. Verify: admin boundary, enabled selection, unique default, partial settings preservation.

### 6. Prompt templates and configuration

Implemented predefined template reference, custom override, tone, and answer-length prompt construction. Disabled templates disappear from new choices; existing references retain configured prompts. Disable is supported instead of destructive deletion.

Created/modified: prompt portions of Phase 5 catalog files/UI and migration; chatbot settings/resolver. Database: templates, template reference, custom instruction and tone. APIs: `/api/prompt-templates`, `/api/admin/prompt-templates`, PATCH by ID. Verify: prompt selection/authorization tests; select, override, save and reopen settings manually.

### 7. Starter questions

Implemented up to ten ordered questions, one per editor line, with add/edit/delete/reordering and display in Playground/public/widget chat.

Created: `migrations/versions/a204_starters.py`. Modified: chatbot model/schema/types/form/detail, Playground, catalog tests. Database: ordered JSON list; no separate question identity needed. API: `starter_questions` in existing chatbot payloads. Verify: starter CRUD/order/limits test.

### 8. XLSX and uploads

Implemented read-only XLSX extraction, bounded archive/workbook validation, sheet/row metadata through chunks/citations, multi-file selection, real drag/drop, per-file validation/errors and completed-file progress. Reused ingestion states/retry/re-index/delete.

Created: `app/rag/loaders/xlsx_loader.py`, `migrations/versions/a205_xlsx_metadata.py`, `tests/test_v2_xlsx.py`.

Modified: requirements/lockfile, validation, loader registry/base, chunker, ingestion, retriever, knowledge/conversation models, source schema/persistence, frontend upload form/source types.

Database: nullable sheet/row fields on chunks and citation snapshots. API: XLSX added to upload policy; multi-file UI calls existing endpoint per file. Verify: XLSX, extraction and ingestion tests; worksheet row preservation asserted.

### 9. Conversation management

Implemented dedicated history/detail/delete UI with channels and dates. External conversations are read-only in Playground.

Created: `frontend/src/features/conversations/conversations.tsx`; conversation portions of `a206_channels.py`. Modified: conversation model/schema/service, frontend types/Playground filtering/chatbot tabs and original tests. Database: channel default PLAYGROUND, guest session hash, integration reference. Existing APIs return channel. Verify: conversation/Playground regression and channel tests.

### 10. Public links and shared chat

Extracted existing orchestration into one shared chat service. Added random public IDs, owner management, guest session boundaries, metadata/chat, enable/disable/regenerate and safe public citations without private download identifiers.

Created: `app/services/chat_service.py`, `app/services/channel_service.py`, `app/repositories/channel_repository.py`, `app/models/channels.py`, `app/schemas/channels.py`, `app/api/channels.py`, `migrations/versions/a206_channels.py`, `tests/test_v2_channels.py`, frontend channel/public-chat services, channel/public-chat components, `frontend/src/app/share/[token]/page.tsx`.

Modified: Playground adapter, model exports/API registration, frontend chatbot tabs/CSS. Database: unified channel integrations unique per chatbot/channel; session-bound conversations. APIs: `/api/chatbots/{id}/channels` and manage routes; `/api/public/share/{token}` and `/ask`. Verify: lifecycle/rotation/session theft/owner tests and live real-RAG acceptance.

### 11. Widget

Implemented iframe chat, isolated launcher styling, random public widget IDs, embed generation/copy, starters/loading/errors and shared chat API.

Created: `frontend/public/widget.js`, `frontend/src/app/widget/[token]/page.tsx`. Reuses Phase 10 backend/UI and migration. APIs: `/api/public/widget/{id}` and `/ask`. Verify: routing, invalid/disabled/rotated IDs and real-RAG acceptance passed. Browser embed interaction remains pending.

### 12. Telegram

Implemented credential encryption, token validation, webhook registration and secret verification, connection tests/status, enable/disable/disconnect, shared chat adapter and retry records.

Created: `app/services/telegram_service.py`, `app/api/telegram.py`, `tests/test_v2_telegram.py`. Modified: channel handling, configuration/dependencies/API registration. Database: encrypted integration fields, unique Telegram bot IDs, update retry records in `a206_channels.py`. APIs: `/api/webhooks/telegram/{identifier}` and channel `/test` plus management routes. Verify: mocked token/encryption/secret/routing/deduplication/disable tests. Real bot delivery is pending configuration. Disconnect Telegram before deleting its chatbot, so the external webhook is removed.

### 13. Source viewer

Implemented worksheet labels, original-file availability checks, “Source unavailable”, and authorized PDF preview links at cited pages. Historical excerpts survive deleted sources.

Created: `backend/tests/test_v2_sources.py`. Modified: `app/api/knowledge.py`, `frontend/src/services/knowledge.service.ts`, `frontend/src/features/playground/source-cards.tsx`. No extra migration beyond Phase 8. APIs: document `/availability` and `/preview`. Verify: historical citation and cross-owner document checks; manual PDF/deletion browser check remains.

### 14. Analytics and admin monitoring

Implemented SQL totals/channel aggregates, user search/details/activation and chatbot owner/status monitoring. Disabled users lose sessions and public channel access. Monitoring omits private credentials/configuration.

Created: `app/repositories/admin_repository.py`, `app/services/admin_service.py`, `app/schemas/admin.py`, `app/api/admin.py`, `tests/test_v2_admin.py`, frontend admin service/users/analytics components. Modified: API registration/workspace. No extra migration; uses existing/new indexed owner/channel relationships. APIs: `/api/analytics`, `/api/admin/analytics`, `/api/admin/users` and detail/PATCH, `/api/admin/chatbots`. Verify: owner/platform aggregate differences, authorization, safe responses and disabled login.

### 15. Security and errors

Reviewed owner checks, public integration/session binding, disabled states, token rotation, password reset, role assignment, prompt/model selection, significant password whitespace, partial settings updates, and secret-safe logging.

Modified: auth schemas/services, chatbot settings merge, shared chat failure logs, Telegram malformed-update handling, source availability, frontend confirmations/success feedback and V2 regression tests. No new migration. Existing CSRF/session/error handling reused.

Limits: single API-process throttle/generation semaphore and durable ingestion worker; no advanced abuse infrastructure. Publishing exposes the configured chatbot knowledge to guests, explained in the Channels screen. Admin monitoring does not grant ownership of private resources.

### 16. Automated and acceptance verification

Created: `backend/scripts/acceptance_v2.py`, `docs/v2-acceptance-results.json`, this report. Modified: PostgreSQL verifier, tests, README and environment examples.

Verified: backend SQLite/PostgreSQL suites; upgrade/downgrade/schema drift; legacy data backfill; TypeScript; lint; webpack production compilation; real Ollama/Qdrant ingestion, grounded cited answers across Playground/share/widget, knowledge/user isolation, channels, aggregates, revocation and disabling.

Remaining acceptance: live Telegram and SMTP require configured external credentials/endpoints; browser visual/iframe interaction requires an available browser. These limitations are explicitly recorded rather than claimed complete.

## Verification snapshot

- SQLite backend suite: **86 passed**, one upstream Starlette/httpx deprecation warning.
- PostgreSQL suite at the migration checkpoint: **83 passed**; legacy owner/role/model backfills, downgrade/upgrade and Alembic drift check passed. Three later disabled-channel/source cases passed on SQLite, plus seven focused channel/Telegram/source regression tests after final safety changes.
- Frontend TypeScript and ESLint: passed without errors.
- Production webpack build: passed; routes include `/`, `/reset-password`, `/share/[token]`, and `/widget/[token]`.
- Real local Ollama/Qdrant/PostgreSQL acceptance: eight recorded checks passed; see `v2-acceptance-results.json`.
- No external credentials were committed, no real Telegram/SMTP message was sent, and test runs did not alter the existing application database. The later local rollout applied the tested migrations after a private database backup.


## Local rollout

The configured API is `http://localhost:8002` (the generic commands above use port 8000; choose the port matching `NEXT_PUBLIC_API_URL`). The local database was backed up and migrated to `a206_channels`, and schema drift check passed. The live API reports 2.0.0 and exposes V2 routes. Docker Compose image tags were updated to V2.

The existing `.env` was tracked by Git despite the no-secrets requirement. It is now removed from tracking, retained on disk, and ignored. Historical Git commits are unchanged. Existing history can still contain those credentials; rotate them before sharing that history.

The prior repository report (`docs/phase-1.md`) flags unexpected obfuscated code in the host npm launcher. Use the Docker toolchain or a trusted Node/npm installation for subsequent commands. This rollout builds the frontend using the official Node container. Host global tooling was not modified.

The local frontend container was rebuilt successfully as `rag-craft-frontend:v2` and started on port 3000. The ingestion worker was started with the V2 code.

Final HTTP readiness returned 200 with API, PostgreSQL, Qdrant, worker, Ollama, generation model and embedding model all `ok`. The frontend returned HTTP 200.
