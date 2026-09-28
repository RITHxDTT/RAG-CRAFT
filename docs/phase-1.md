> Update after phases 2–5: frontend lint, TypeScript, and production builds now pass in an official Node Docker image. Host npm remains untouched. The following is the original Phase 1 record; its frontend build blocker has been worked around with the isolated toolchain. Interactive browser verification remains unavailable.

# Phase 1 implementation record

## Implemented

- FastAPI application, environment configuration, and separate health routes/services.
- SQLAlchemy connection factory with environment-based database configuration.
- Liveness and dependency readiness endpoints with clean unavailable statuses.
- Explicit CORS origins for the local Next.js app.
- PostgreSQL 17 and Qdrant Compose services with persistent volumes and localhost-only ports.
- Backend and frontend Dockerfiles (builds not verified).
- Centralized typed frontend API layer and a connection page with loading/error/retry states.
- Four backend tests covering liveness, allowed/disallowed CORS origins, and dependency failure reporting.
- Generated ignored local environment configuration without printing credentials.

## Verification

Run instructions and the small phase check are in the root README. Four backend tests pass. Live GET /health and GET /health/ready both return HTTP 200; API, PostgreSQL, Qdrant, and the existing Ollama service all report ok. PostgreSQL runs on host port 55432 because 5432 and 5433 are occupied. FastAPI is running on localhost:8000. Model availability and inference have not been tested. Frontend lint/build did not complete. The existing frontend Git history remains untouched; root repository consolidation is pending.

## Tooling blocker

The machine's global npm launcher `/usr/local/lib/node_modules/npm/lib/cli.js` contains a large obfuscated script appended after its normal entry point. It is 75,656 bytes and includes markers `/*C260512A*/` and `/*RS260605*/`.

SHA-256 observed:

`f04b6e749639fe70e2fb7cd46a442b245b3d44af275de9f6e68e96a8d6e0be30`

The first npm lint invocation failed before configuration resolved, reporting a DNS lookup for `fullnode.mainnet.aptoslabs.com`. A retry outside the sandbox was launched before the file inspection result was available; it was interrupted after the appended code was discovered. This is evidence of unexpected modification, not a complete forensic determination of its effects. No global tooling files were edited or deleted.

Use a trusted Node/npm installation and inspect the host before running npm again. This environment issue is outside the project application code. Frontend validation and the Phase 1 gate remain incomplete, so later phases have not started, following section 44 of `setting.txt`: “Do not move to the next phase until the current phase works.”
