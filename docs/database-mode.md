# Database mode (Spring services + craftrag_db)

The frontend has two data sources, chosen with `NEXT_PUBLIC_DATA_SOURCE`:

| Value | Where data lives | Needs a backend |
|---|---|---|
| `local` (default) | The browser (localStorage / IndexedDB). This is the Vercel demo. | No |
| `api` | PostgreSQL `craftrag_db` through the Spring services and the API gateway. | Yes |

## What uses the database today

| Area | Service (port) | Schema in `craftrag_db` |
|---|---|---|
| Sign up / in, profile, password reset, e-mail verification, lock-out, account deletion and restore, quotas, admin user actions, audit log | identity-service (8081) | `identity` |
| Chat models, embedding models, prompt templates, admin limits for advanced settings | catalog-service (8082) | `catalog` |
| Chatbots, settings, lifecycle (DRAFT / PENDING / ACTIVE / PAUSED / DISABLED), publish checklist, appeals, admin disable | chatbot-service (8083) | `chatbot` |
| Documents (upload, simulated ingestion, replace, retry, delete) | knowledge-service (8084) | `knowledge` |
| Share link, web widget, Telegram (simulated) | channel-service (8086) | `channel` |

Everything goes through the gateway (default `http://localhost:8090`). Not on the database yet, still browser-local in `api` mode:
playground and conversations, public guest chat, answer feedback and reports, analytics and monitoring, notifications, MFA, passkeys,
social sign-in, session list, site crawl preview, renaming documents. Guest links therefore only work in the browser that created them.

The local-only screens read chatbots, documents and channels from a cache that is refreshed after every server read, so they keep working on
database-backed chatbots.

## Run it

```sh
cp .env.craftrag.example .env.craftrag        # then set CRAFTRAG_DB_PASSWORD (never commit this file)
services/scripts/run-craftrag.sh up           # builds on first run, starts 5 services + the gateway, waits until ready
# frontend/.env.local:
#   NEXT_PUBLIC_DATA_SOURCE=api
#   NEXT_PUBLIC_API_URL=http://localhost:8090
cd frontend && npm run dev                    # restart it after changing .env.local
services/scripts/run-craftrag.sh status | down
```

The schema is created once from `database/craftrag_schema.sql` (Flyway is turned off for `craftrag_db`). Logs: `services/.run/*.log`.
Demo accounts (`admin@gmail.com`, `user@gmail.com`, password `123`) are created by identity-service while `SEED_DEMO_ACCOUNTS=true`.

## Tests

```sh
cd services && mvn test                                        # 34 tests on in-memory H2, no database needed
python3 services/scripts/smoke-craftrag.py http://localhost:8090   # 54 checks through the gateway against craftrag_db
cd frontend && npm run test:api                                # 41 checks: the real frontend services in API mode
npm run test:demo && npm run test:v5                           # browser-only mode, unchanged
```

The two live smoke tests create throw-away data and remove it. The admin actions they perform stay in `identity.audit_logs` on purpose:
that table is append-only.

## "Cannot reach the API at … from …"

The browser could not complete the request. Check, in this order:

1. Is the gateway up? `services/scripts/run-craftrag.sh status`, then `curl http://localhost:8090/health/ready`.
2. Is the page's address listed in `CORS_ORIGINS` in `.env.craftrag` (default `http://localhost:3000,http://127.0.0.1:3000`)? The message names both addresses.
   Restart the gateway after changing it.
3. Opening the app from another machine or VM? `localhost` then means that machine: point `NEXT_PUBLIC_API_URL` at the gateway host's IP.

A browser reports a CORS block exactly like a dead server, and treats a response with two `Access-Control-Allow-Origin` headers as a block too.
The gateway therefore owns CORS and strips the downstream services' own CORS headers (covered by `CorsProxyTest`).

## Things to know

- Sessions are JWTs kept in localStorage and sent as a Bearer header. Suspending a user, a forced logout or a password reset revokes the token;
  other services re-check with identity-service every 5 seconds at most. This is demo-phase security, not production hardening.
- `SEED_DEMO_ACCOUNTS=true` puts `admin@gmail.com / 123` into the database. Set it to `false` and use `ADMIN_EMAIL` / `ADMIN_PASSWORD` for anything real.
- Share-link passwords are still stored inside the channel settings JSON by channel-service. The schema has an `access_password_hash` column ready for the fix.
- conversation-service and analytics-service are not started in this phase. The `conversations` table only accepts PLAYGROUND chats (guest conversations are
  never stored), so the existing guest-chat code in those services must be changed before they can run against this schema.
- Port 5432 on the database host listens on all interfaces: restrict who can reach it, and change the default passwords.
