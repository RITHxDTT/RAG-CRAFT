# Version 2 — Phase 1 architecture inspection

Inspected on 2026-09-29. This is the first checkpoint required by `v2.txt`, section 29. Version 2 features have not been implemented in this phase.

## Existing application

The repository contains a FastAPI backend and a Next.js 16 / React 19 frontend with TypeScript and Tailwind 4. The frontend directory has its own Git working tree and existing uncommitted UI changes; preserve those changes throughout V2.

The frontend calls FastAPI through `frontend/src/services/api.ts` and feature-specific services. `src/features/auth/admin-app.tsx` handles the session and `src/features/workspace/workspace.tsx` provides the dashboard shell. Feature components already implement chatbot CRUD, document management, Playground, conversation history, and expandable citation cards. Navigation within the workspace currently uses component state. Public share and widget pages should be added through App Router while reusing chat presentation components where practical.

Backend HTTP routes in `backend/app/api/` delegate to services. Repositories contain scoped database queries. Some transaction and aggregate queries currently live in services; extend the repository boundary as these areas change instead of duplicating operations.

PostgreSQL is the application source of truth. Qdrant stores vectors. Ollama supplies embeddings and answers. Compose defines PostgreSQL and Qdrant, optional frontend/backend profiles, and a separate durable ingestion worker. The worker uses PostgreSQL advisory locking, existing ingestion jobs, and vector cleanup jobs; no new queue infrastructure is needed.

## Database and migrations

Existing tables:

| Area | Tables |
| --- | --- |
| Identity | users, organizations, organization_members |
| Chatbot | chatbots, chatbot_settings |
| Knowledge | knowledge_sources, documents, document_chunks, ingestion_jobs |
| Conversations | conversations, messages, message_sources |
| Administration | api_keys, audit_logs |
| Cleanup | vector_cleanup_jobs |

Records use UUID identifiers and timestamps. Foreign keys, checks, indexes, and unique constraints already cover the core relationships.

Migration chain:

1. `43dab0e7e4d1`: fourteen-table foundation.
2. `605e885948c8`: durable vector cleanup and historical citation snapshots.
3. `9bc7c261b302`: stable per-conversation message sequence, including existing-row backfill.

V2 must add migrations after the existing head. Do not recreate these tables. Existing documents and conversations inherit their organization through the chatbot. Preserve citation snapshots and deferred vector cleanup when extending deletion behavior.

## Authentication and authorization

`core/security.py` hashes passwords with pwdlib's recommended hasher. Sessions use signed JWTs in an HttpOnly cookie, with configurable Secure and SameSite=Lax. Tokens include user, organization, expiration, and token version. Logout increments the version to revoke existing sessions. Mutating authenticated routes use the existing request-protection header and origin validation.

`auth_service.py` currently requires ADMIN organization membership for both login and authentication. User activation is checked on both paths. Profile responses hard-code ADMIN. There is no platform role on users, full name, registration, profile editing, or password reset flow.

V2 should separate authenticated-user context from the platform-admin dependency, preserving cookie/session protections. Platform roles must not be inferred from organization membership: a customer can own an organization without becoming a platform administrator. Public registration must assign USER server-side. Password reset needs expiring, single-use hashed tokens and a configured delivery mechanism; never return reset credentials from production public endpoints.

## Ownership gap

Chatbot repositories filter by `organization_id`; chatbots do not yet have an individual owner. This isolates organizations but cannot enforce the V2 rule for two users in the same organization.

Add an indexed chatbot owner foreign key and resolve ownership from authenticated context. Descendants can inherit ownership through the chatbot rather than duplicating owner columns everywhere. Check every read and write, including document detail/download, recent activity, conversations, dashboard aggregates, and integration operations. Several existing detail queries directly check organization scope without calling `require_chatbot`; updating only that helper would leave gaps.

Backfill existing ownership from reliable creator audit records or an unambiguous existing organization administrator. Do not arbitrarily assign legacy chatbots in organizations with multiple candidate owners. A migration should fail clearly on ambiguous data and provide a reviewed mapping path. Preserve existing admin access through an explicit legacy-role backfill, and use USER as the default for future registrations.

## RAG and conversations

Upload validation supports PDF, DOCX, TXT, and MD. Document upload stores a source, document, and queued ingestion job. The worker extracts text, cleans/chunks it, generates embeddings, writes vectors and chunk records, and marks the document READY or FAILED. Retry, re-index, deletion, and durable cleanup already exist.

`rag/vector_store.py` applies both organization and chatbot filters during Qdrant search, plus READY document IDs and embedding model. `rag/retriever.py` resolves returned chunk IDs against PostgreSQL and repeats organization/chatbot/READY filtering rather than trusting vector payloads. Preserve both defenses.

`rag/pipeline.py` retrieves context, builds grounded prompts, calls the generator, and returns cited answers or an insufficient-knowledge answer. The pipeline already has no channel-specific transport logic.

`services/playground_service.py` currently owns the reusable chat transaction: authorize chatbot, validate status, load history, call the RAG pipeline, save ordered messages and citation snapshots, update the conversation timestamp, and commit. Extract this orchestration into a shared chat service when adding channels. Keep Playground as an authenticated adapter. Maintain rollback behavior and message ordering.

Conversations currently have no channel or guest-session identity. Public channels must bind conversations to both the resolved chatbot/integration and a guest session, so knowing a conversation identifier cannot grant another visitor's history. Backfill existing conversations to PLAYGROUND. Public metadata must expose only approved display fields and citations, not internal configuration or owner information.

## Preserve the updated modern design

Use the current `frontend/src/app/globals.css` and existing feature components as the design reference:

- Dark navy sidebar (`#0f172a`) with teal active states and accents (`#0d9488`).
- Pale slate page background (`#f8fafc`), white cards, subtle borders and shadows.
- Rounded panels and controls, the existing typography stack, compact Lucide icons.
- Existing `panel`, `form-stack`, `form-columns`, `primary`, `danger`, `badge`, `error`, `success-note`, and empty/loading state patterns.
- Keep current responsive layouts; make user/admin navigation role-aware within the same shell.

Read the installed Next.js guides required by `frontend/AGENTS.md` before frontend implementation. New pages must include accessible labels, keyboard focus, pending states, success/error feedback, and confirmations for destructive actions.

## Incremental implementation map

Follow the order in `v2.txt`; verify and report each phase before advancing.

| Phase | Extend existing foundation |
| --- | --- |
| 2 | User role and owner migration, legacy backfill, scoped repository foundation |
| 3 | Registration, login, logout, profile, password reset using existing sessions |
| 4 | User-owned chatbot CRUD and authorization regression tests |
| 5 | Backend model catalog, admin management, enabled-model selection |
| 6 | Prompt template reference plus custom override, tone/settings prompt construction |
| 7 | Ordered starter questions and configuration UI |
| 8 | XLSX loader and sheet/row metadata through chunks/citations; multiple-file upload UX |
| 9 | Channel-aware conversation list/detail/delete |
| 10 | Shared chat service and revocable random public share tokens |
| 11 | Random widget IDs, iframe chat page, configurable embed loader |
| 12 | Encrypted Telegram credentials, validation, webhook verification and adapter |
| 13 | Extend citation cards with PDF page viewing and unavailable-source handling |
| 14 | Scoped SQL aggregates and admin monitoring/user management |
| 15 | Review authorization, public session boundaries, errors, secret-safe logging |
| 16 | Automated coverage and live acceptance scenario |

Model/template/channel tables should be introduced in their relevant phases. Never authorize management using frontend owner IDs. Public token regeneration must invalidate the previous token. Telegram credentials must be redacted from responses and logs; transport details must remain inside its adapter.

## Phase changes and verification

Created: `docs/v2-phase-1.md`.

Modified: none. Database changes: none. API changes: none. Existing UI changes are preserved.

Run baseline checks from the repository root:

```sh
.venv/bin/python -m pytest -q
cd frontend
npm run lint
```

Results on 2026-09-29:

- Backend: 69 passed, one Starlette/httpx deprecation warning.
- Frontend lint: zero errors, two existing unused-import warnings (`FileText` in chatbot-form and `Sparkles` in playground).
- npm initially failed during startup with a sandbox DNS error; the approved rerun completed successfully.

These checks are not a live PostgreSQL/Qdrant/Ollama acceptance run or a browser visual check. The existing test fixtures use SQLite unless TEST_DATABASE_URL is set. No production-like schema was modified.

For local runtime setup, use the existing README instructions for environment configuration, Docker services, Alembic migrations, backend, worker, Ollama, and frontend. This documentation-only phase introduces no new runtime command.

Manual baseline verification: log in with the existing admin, create a chatbot, upload a supported document, wait for READY, ask a supported question, open its citation, reopen the conversation, and confirm another chatbot cannot retrieve the first chatbot's knowledge. The checkpoint is ready for review before Phase 2.
