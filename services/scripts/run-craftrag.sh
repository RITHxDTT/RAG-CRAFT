#!/usr/bin/env bash
# Runs the services against the hosted PostgreSQL database craftrag_db (one schema per service). Flyway is OFF because the
# schema is created once from database/craftrag_schema.sql.
#
#   cp .env.craftrag.example .env.craftrag     # set CRAFTRAG_DB_PASSWORD
#   services/scripts/run-craftrag.sh up        # build (if needed), start services, wait for the gateway
#   services/scripts/run-craftrag.sh status
#   services/scripts/run-craftrag.sh down
#
# Requires Java 17+ and Maven. Logs and pids live in services/.run/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES="$ROOT/services"
RUN="$SERVICES/.run"
ENV_FILE="${ENV_FILE:-$ROOT/.env.craftrag}"
# Services that talk to the database, in start order. conversation-service and analytics-service are not part of this phase.
MODULES=(identity-service catalog-service chatbot-service knowledge-service channel-service api-gateway)

schema_for() {
  case "$1" in
    identity-service) echo identity ;; catalog-service) echo catalog ;; chatbot-service) echo chatbot ;;
    knowledge-service) echo knowledge ;; channel-service) echo channel ;; *) echo "" ;;
  esac
}
port_for() {
  case "$1" in
    identity-service) echo 8081 ;; catalog-service) echo 8082 ;; chatbot-service) echo 8083 ;; knowledge-service) echo 8084 ;;
    channel-service) echo 8086 ;; api-gateway) echo "${GATEWAY_PORT:-8080}" ;;
  esac
}

load_env() {
  [ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE. Copy .env.craftrag.example and set CRAFTRAG_DB_PASSWORD."; exit 1; }
  set -a; . "$ENV_FILE"; set +a
  : "${CRAFTRAG_DB_PASSWORD:?Set CRAFTRAG_DB_PASSWORD in $ENV_FILE}"
  : "${JWT_SECRET:?Set JWT_SECRET in $ENV_FILE}"
  : "${INTERNAL_TOKEN:?Set INTERNAL_TOKEN in $ENV_FILE}"
}

jar_for() { ls "$SERVICES/$1"/target/*.jar 2>/dev/null | grep -v original | head -1 || true; }

case "${1:-up}" in
  up)
    load_env
    mkdir -p "$RUN"
    for module in "${MODULES[@]}"; do
      if [ -z "$(jar_for "$module")" ]; then
        echo "building (first run)..."; (cd "$SERVICES" && mvn -q -DskipTests install); break
      fi
    done
    host="${CRAFTRAG_DB_HOST:-localhost}"; port="${CRAFTRAG_DB_PORT:-5432}"; name="${CRAFTRAG_DB_NAME:-craftrag_db}"
    for module in "${MODULES[@]}"; do
      if [ -f "$RUN/$module.pid" ] && kill -0 "$(cat "$RUN/$module.pid")" 2>/dev/null; then echo "$module already running"; continue; fi
      schema="$(schema_for "$module")"
      env_args=(JWT_SECRET="$JWT_SECRET" INTERNAL_TOKEN="$INTERNAL_TOKEN" CORS_ORIGINS="${CORS_ORIGINS:-http://localhost:3000}"
                SEED_DEMO_ACCOUNTS="${SEED_DEMO_ACCOUNTS:-true}" AUTO_VERIFY_EMAIL="${AUTO_VERIFY_EMAIL:-true}"
                PORT="$(port_for "$module")" RAGCRAFT_REMOTE_TOKEN_VALIDATION=true)
      if [ -n "$schema" ]; then
        env_args+=(DB_URL="jdbc:postgresql://$host:$port/$name?currentSchema=$schema" DB_USER="${CRAFTRAG_DB_USER:-ragcraft_user}"
                   DB_PASSWORD="$CRAFTRAG_DB_PASSWORD" SPRING_FLYWAY_ENABLED=false)
      fi
      env "${env_args[@]}" nohup java -jar "$(jar_for "$module")" > "$RUN/$module.log" 2>&1 &
      echo $! > "$RUN/$module.pid"
      echo "started $module on :$(port_for "$module") (schema: ${schema:-none})"
    done
    echo "waiting for the gateway..."
    for _ in $(seq 1 90); do
      # conversation-service and analytics-service are not started in this phase, so the gateway reports "degraded"; wait for ours.
      health="$(curl -fsS "http://localhost:${GATEWAY_PORT:-8080}/health/ready" 2>/dev/null || true)"
      if echo "$health" | grep -q '"identity":"ok"' && echo "$health" | grep -q '"catalog":"ok"' && echo "$health" | grep -q '"chatbot":"ok"' \
         && echo "$health" | grep -q '"knowledge":"ok"' && echo "$health" | grep -q '"channel":"ok"'; then
        echo "ready: http://localhost:${GATEWAY_PORT:-8080}"; exit 0
      fi
      sleep 2
    done
    echo "not ready yet; see $RUN/*.log"; exit 1
    ;;
  status)
    for module in "${MODULES[@]}"; do
      if [ -f "$RUN/$module.pid" ] && kill -0 "$(cat "$RUN/$module.pid")" 2>/dev/null; then echo "up    $module"; else echo "down  $module"; fi
    done
    ;;
  down)
    for module in "${MODULES[@]}"; do
      [ -f "$RUN/$module.pid" ] && kill "$(cat "$RUN/$module.pid")" 2>/dev/null || true
      rm -f "$RUN/$module.pid"
    done
    echo "stopped"
    ;;
  *) echo "usage: $0 up|status|down"; exit 2 ;;
esac
