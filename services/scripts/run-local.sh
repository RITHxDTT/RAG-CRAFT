#!/usr/bin/env bash
# Starts the seven PostgreSQL containers and runs every service jar locally (fast dev loop without image builds).
#
#   services/scripts/run-local.sh up      # start databases + services (logs in services/.run/*.log)
#   services/scripts/run-local.sh down    # stop services + databases
#   PROFILE=local services/scripts/run-local.sh up   # no Docker: every service uses its in-memory H2 database
#
# Requires: Java 17+, Docker, a prior `mvn -q -DskipTests install` in services/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES="$ROOT/services"
RUN="$SERVICES/.run"
ENV_FILE="${ENV_FILE:-$ROOT/.env.microservices}"
[ -f "$ENV_FILE" ] || ENV_FILE="$ROOT/.env.microservices.example"
COMPOSE=(docker compose -f "$ROOT/docker-compose.microservices.yml" --env-file "$ENV_FILE")
DBS=(identity-db catalog-db chatbot-db knowledge-db conversation-db channel-db analytics-db)
MODULES=(identity-service catalog-service chatbot-service knowledge-service conversation-service channel-service analytics-service api-gateway)

case "${1:-up}" in
  up)
    mkdir -p "$RUN"
    set -a; . "$ENV_FILE"; set +a
    PROFILE_ARGS=()
    if [ "${PROFILE:-}" = "local" ]; then
      echo "PROFILE=local: using in-memory H2 databases, no Docker"
      PROFILE_ARGS=(--spring.profiles.active=local)
    else
      "${COMPOSE[@]}" up -d "${DBS[@]}"
      echo "waiting for databases..."
      for db in "${DBS[@]}"; do
        until [ "$("${COMPOSE[@]}" ps --format '{{.Health}}' "$db")" = "healthy" ]; do sleep 1; done
      done
    fi
    for module in "${MODULES[@]}"; do
      jar=$(ls "$SERVICES/$module"/target/*.jar | grep -v original | head -1)
      SEED_DEMO_ACCOUNTS="${SEED_DEMO_ACCOUNTS:-true}" nohup java -jar "$jar" ${PROFILE_ARGS[@]+"${PROFILE_ARGS[@]}"} > "$RUN/$module.log" 2>&1 &
      echo $! > "$RUN/$module.pid"
      echo "started $module (pid $(cat "$RUN/$module.pid"))"
    done
    echo "waiting for the gateway..."
    for _ in $(seq 1 90); do
      if curl -fsS http://localhost:8080/health/ready 2>/dev/null | grep -q '"status":"ok"'; then
        echo "all services ready: http://localhost:8080"; exit 0
      fi
      sleep 2
    done
    echo "services did not become ready; see $RUN/*.log"; exit 1
    ;;
  down)
    for module in "${MODULES[@]}"; do
      [ -f "$RUN/$module.pid" ] && kill "$(cat "$RUN/$module.pid")" 2>/dev/null || true
      rm -f "$RUN/$module.pid"
    done
    [ "${PROFILE:-}" = "local" ] || "${COMPOSE[@]}" stop "${DBS[@]}" 2>/dev/null || true
    echo "stopped"
    ;;
  *) echo "usage: $0 up|down"; exit 2 ;;
esac
