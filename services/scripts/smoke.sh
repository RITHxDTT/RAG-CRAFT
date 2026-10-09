#!/usr/bin/env bash
# End-to-end smoke test through the API gateway: sign in, create a chatbot, upload a document,
# wait for the simulated ingestion, ask a question, publish a share link, chat as a guest, read analytics.
#
#   services/scripts/smoke.sh                 # against http://localhost:8080
#   GATEWAY=http://localhost:8080 services/scripts/smoke.sh
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
EMAIL="${SMOKE_EMAIL:-user@gmail.com}"
PASSWORD="${SMOKE_PASSWORD:-123}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

json() { python3 -c "import json,sys; data=json.load(sys.stdin); print(eval('data'+sys.argv[1]))" "$1"; }
step() { printf '\n== %s\n' "$1"; }

step "gateway readiness"
curl -fsS "$GATEWAY/health/ready" | python3 -m json.tool

step "sign in as $EMAIL"
TOKEN=$(curl -fsS -X POST "$GATEWAY/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | json "['access_token']")
AUTH=(-H "Authorization: Bearer $TOKEN")
curl -fsS "$GATEWAY/api/auth/me" "${AUTH[@]}" | json "['email']"

step "create chatbot"
BOT=$(curl -fsS -X POST "$GATEWAY/api/chatbots" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"name":"Smoke Assistant","description":"Created by smoke.sh","starter_questions":["What is the annual leave policy?"],"settings":{"tone":"FRIENDLY"}}')
BOT_ID=$(echo "$BOT" | json "['id']")
echo "chatbot $BOT_ID ($(echo "$BOT" | json "['settings']['model_name']"))"

step "upload a text document"
printf 'Employees receive 18 days of annual leave.\n\nRemote work is allowed three days per week.\n' > "$TMP/handbook.txt"
DOC_ID=$(curl -fsS -X POST "$GATEWAY/api/chatbots/$BOT_ID/documents" "${AUTH[@]}" -F "file=@$TMP/handbook.txt" | json "['id']")
for _ in $(seq 1 20); do
  STATUS=$(curl -fsS "$GATEWAY/api/chatbots/$BOT_ID/documents/$DOC_ID" "${AUTH[@]}" | json "['status']")
  echo "  status: $STATUS"
  [ "$STATUS" = "READY" ] && break
  sleep 1
done
[ "$STATUS" = "READY" ] || { echo "document did not become READY"; exit 1; }

step "ask in the playground"
curl -fsS -X POST "$GATEWAY/api/chatbots/$BOT_ID/ask" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"question":"What is the annual leave policy?"}' | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["answer"]); print("sources:", [s["document_name"] for s in d["sources"]])'

step "publish a share link and chat as a guest"
curl -fsS -X PATCH "$GATEWAY/api/chatbots/$BOT_ID" "${AUTH[@]}" -H 'Content-Type: application/json' -d '{"status":"ACTIVE"}' > /dev/null
LINK=$(curl -fsS -X POST "$GATEWAY/api/chatbots/$BOT_ID/channels" "${AUTH[@]}" -H 'Content-Type: application/json' -d '{"channel":"PUBLIC_LINK"}')
PUBLIC_ID=$(echo "$LINK" | json "['public_id']")
echo "guest url: $(echo "$LINK" | json "['url']")"
curl -fsS "$GATEWAY/api/public/share/$PUBLIC_ID" | json "['name']"
curl -fsS -X POST "$GATEWAY/api/public/share/$PUBLIC_ID/ask" -H 'Content-Type: application/json' -d '{"question":"Hello"}' | json "['answer']"

step "analytics"
sleep 1
curl -fsS "$GATEWAY/api/analytics?days=7" "${AUTH[@]}" | python3 -c 'import json,sys; d=json.load(sys.stdin)["usage"]; print({k: d[k] for k in ("total_chatbots","total_knowledge","total_messages","active_channels")})'

step "internal endpoints are hidden behind the gateway"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/internal/chatbots/$BOT_ID" -H 'X-Internal-Token: anything')
[ "$CODE" = "404" ] && echo "blocked (404)" || { echo "expected 404, got $CODE"; exit 1; }

step "cleanup"
curl -fsS -X DELETE "$GATEWAY/api/chatbots/$BOT_ID" "${AUTH[@]}" -o /dev/null -w 'deleted chatbot (%{http_code})\n'
echo
echo "SMOKE PASS"
