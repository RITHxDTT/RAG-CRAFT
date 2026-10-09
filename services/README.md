# RAG Craft microservices (Spring Boot)

The FastAPI monolith in `backend/` is split into seven Spring Boot services, each owning its own
PostgreSQL database, behind one API gateway. Paths and JSON shapes match the FastAPI API and the
Next.js `frontend/` types, so the frontend can switch from browser storage to the gateway without changing contracts.

```text
Next.js (3000)
   │
   ▼
api-gateway (8080)  ── routes /api/** by path, blocks /api/internal/**
   ├── identity-service      8081  identity_db      users, organizations, auth, password reset, admin users
   ├── catalog-service       8082  catalog_db       LLM models, system prompt templates
   ├── chatbot-service       8083  chatbot_db       chatbots, settings, dashboard, admin monitor
   ├── knowledge-service     8084  knowledge_db     sources, documents, chunks, ingestion jobs, file storage
   ├── conversation-service  8085  conversation_db  ask / compare, conversations, messages, citations
   ├── channel-service       8086  channel_db       share links, web widget, Telegram, guest chat, webhook
   └── analytics-service     8087  analytics_db     message events, user and admin analytics overview
```

## How the services work together

- **Authentication**: identity-service issues an HS256 JWT (claims `sub`, `org`, `ver`, `role`, `email`,
  `iss=rag-craft`, `aud=rag-craft-admin`). Every service validates it with the shared `JWT_SECRET`; identity
  additionally enforces revocation (`token_version`). Tokens travel as `Authorization: Bearer` or the
  `ragcraft_session` cookie.
- **Ownership**: resources reference chatbots and users by id only (no cross-database foreign keys).
  knowledge, conversation and channel services call `GET /api/internal/chatbots/{id}` on chatbot-service and
  check `owner_id` against the caller (admins may access everything), exactly like `require_chatbot` in FastAPI.
- **Service-to-service calls** use `/api/internal/**` endpoints protected by the `X-Internal-Token` header
  (`INTERNAL_TOKEN`). The gateway never forwards `/api/internal/**`.
- **Cascades**: deleting a chatbot asks knowledge, conversation and channel services to remove their rows;
  deleting a user asks chatbot-service to remove the owner's chatbots. Each service owns its own cleanup.
- **Analytics**: conversation-service posts one event per exchange; analytics-service combines those with
  live counts from each service's `/api/internal/stats`.
- **Mock first**: knowledge-service simulates the ingestion pipeline (`PROCESSING_MODE=mock`) and
  conversation-service builds simulated answers with real citations from stored chunks (`RAG_MODE=mock`).
  Switching both to `external` hands the work to the FastAPI RAG service (Qdrant + Ollama) in the next phase.

## Run locally without Docker (in-memory databases)

Every service has a `local` profile backed by H2, so a single service can be started and tested alone:

```sh
cd services
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # Java 17+ (JDK 17 is installed via Homebrew)
mvn -q install                                        # builds all modules and runs every test on H2
cd identity-service && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Run everything with Docker (one PostgreSQL per service)

```sh
cp .env.microservices.example .env.microservices    # edit the secrets
docker compose -f docker-compose.microservices.yml --env-file .env.microservices up --build
services/scripts/smoke.sh                            # login → chatbot → upload → ask → share link → analytics
```

Databases are published on `localhost:5433` (identity) through `5439` (analytics) for inspection.

Port note: this machine already runs Jenkins on `127.0.0.1:8080`. The gateway binds all interfaces, so
`http://[::1]:8080` reaches it, but to avoid the clash set `PORT=8090` for `api-gateway` (and `PUBLIC_API_URL`
plus the frontend's API base URL to match).

For a fast local loop without image builds, `services/scripts/run-local.sh up` starts the seven databases and
runs the built jars; `PROFILE=local services/scripts/run-local.sh up` does the same with in-memory H2 and no Docker.
Flyway creates each schema on first start; demo accounts `admin@gmail.com` and `user@gmail.com`
(password `123`) are seeded when `SEED_DEMO_ACCOUNTS=true`.

## Module layout

Each service follows the same structure, kept deliberately small for students and team members:

```text
<service>/src/main/java/com/ragcraft/<domain>/
  <Domain>Application.java    Spring Boot entry point (scans com.ragcraft so `common` beans load)
  domain/                     JPA entities (UUID ids, created_at / updated_at)
  repository/                 Spring Data repositories
  service/                    business rules and ownership checks
  api/                        REST controllers and request/response records (snake_case JSON)
  client/                     typed calls to other services
src/main/resources/db/migration/V1__*.sql   Flyway schema (portable between PostgreSQL and H2)
```

`common/` holds the shared JWT filter, security rules, error contract (`{"detail": ...}`),
service client, base entity and health endpoints.

## Not in this phase

- The RAG pipeline (text extraction for PDF/DOCX/XLSX, embeddings, Qdrant, Ollama) stays in `backend/`
  and is reached later through `RAG_MODE=external` and `PROCESSING_MODE=external`.
- Email delivery for password resets (`MAIL_MODE=log` prints the token instead).
- Live Telegram traffic needs a public `PUBLIC_API_URL` and `TELEGRAM_MODE=live`.
