#!/usr/bin/env python3
"""End-to-end check of the services running against craftrag_db, through the API gateway (standard library only).

  services/scripts/run-craftrag.sh up
  python3 services/scripts/smoke-craftrag.py [http://localhost:8090]

It registers a throw-away user, walks a chatbot through its whole lifecycle (document -> pending -> channel -> publish -> pause ->
admin disable -> appeals -> re-enable), checks the audit log, quota and suspension, then removes everything it created.
Requires the demo accounts (SEED_DEMO_ACCOUNTS=true): admin@gmail.com / user@gmail.com, password 123.
"""
import json
import secrets
import sys
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8090").rstrip("/")
passed = failed = 0


def call(method, path, body=None, token=None, raw=None, headers=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as error:
        text = error.read().decode()
        try:
            return error.code, json.loads(text)
        except ValueError:
            return error.code, {"detail": text}


def check(label, condition, detail=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  PASS  {label}")
    else:
        failed += 1
        print(f"  FAIL  {label}  {detail}")


def expect(label, response, want, **fields):
    code, body = response
    ok = code == want and all((body or {}).get(k) == v for k, v in fields.items())
    check(label, ok, f"(got {code} {json.dumps(body)[:200]})")
    return body


def multipart(name, filename, content):
    boundary = "----smoke" + secrets.token_hex(8)
    payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n"
               f"Content-Type: text/plain\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return payload, {"Content-Type": f"multipart/form-data; boundary={boundary}"}


def login(email, password):
    code, body = call("POST", "/api/auth/login", {"email": email, "password": password})
    assert code == 200, f"login {email} failed: {code} {body}"
    return body["access_token"]


print(f"Smoke test against {BASE}")
health = call("GET", "/health/ready")[1] or {}
check("gateway reaches identity, catalog, chatbot, knowledge and channel",
      all(health.get("services", {}).get(s) == "ok" for s in ("identity", "catalog", "chatbot", "knowledge", "channel")), json.dumps(health))

admin = login("admin@gmail.com", "123")
run = secrets.token_hex(4)
email, password = f"smoke-{run}@example.com", "smoke-pass-" + run
created_user = created_bot = None

try:
    print("\nSign up and account rules")
    expect("weak password rejected", call("POST", "/api/auth/register", {"full_name": "S", "email": email, "password": "short", "confirm_password": "short", "terms": True}), 400)
    expect("terms must be accepted", call("POST", "/api/auth/register", {"full_name": "S", "email": email, "password": password, "confirm_password": password, "terms": False}), 400)
    registered = expect("register", call("POST", "/api/auth/register", {"full_name": "Smoke Tester", "email": email, "password": password, "confirm_password": password, "terms": True}), 201)
    created_user = registered["user"]["id"]
    user = registered["access_token"]
    expect("wrong password gives the generic message", call("POST", "/api/auth/login", {"email": email, "password": "wrong-pass-1"}), 401, detail="Invalid email or password.")
    me = call("GET", "/api/auth/me", token=user)[1]
    check("profile has V5 fields (status, language, timezone)", me["status"] == "ACTIVE" and me["language"] == "en" and me["timezone"] == "UTC", json.dumps(me))
    expect("profile password change needs the current password", call("PATCH", "/api/auth/me", {"password": "another-pass-9", "confirm_password": "another-pass-9"}, user), 400)

    print("\nCatalog and quota")
    models = call("GET", "/api/models", token=user)[1]
    embeddings = call("GET", "/api/embedding-models", token=user)[1]
    limits = call("GET", "/api/settings-limits", token=user)[1]
    check("chat models and embedding models are separate lists", len(models) >= 1 and all(m["kind"] == "LLM" for m in models) and len(embeddings) >= 1 and all(m["kind"] == "EMBEDDING" for m in embeddings))
    check("six admin-defined advanced limits", len(limits) == 6, json.dumps(limits))
    quota = call("GET", "/api/quota", token=user)[1]
    check("quota starts at 0 / 5 bots", quota["bots"] == {"used": 0, "limit": 5}, json.dumps(quota))

    print("\nChatbot creation and validation")
    expect("name over 50 characters rejected", call("POST", "/api/chatbots", {"name": "x" * 51}, user), 422)
    expect("temperature above the admin limit rejected", call("POST", "/api/chatbots", {"name": "T", "settings": {"temperature": 1.5}}, user), 400)
    expect("unknown tone rejected", call("POST", "/api/chatbots", {"name": "T", "settings": {"tone": "SHOUTY"}}, user), 400)
    bot_name = f"Smoke Bot {run}"
    bot = expect("create chatbot (status requested ACTIVE is ignored)", call("POST", "/api/chatbots", {
        "name": bot_name, "description": "end to end", "status": "ACTIVE", "starter_questions": ["What is the refund policy?"],
        "settings": {"model_id": models[0]["id"], "tone": "FRIENDLY", "top_k": 4, "embedding_model": embeddings[0]["model_identifier"]}}, user), 201, status="DRAFT")
    created_bot = bot["id"]
    check("settings stored with admin defaults", bot["settings"]["top_k"] == 4 and bot["settings"]["temperature"] == 0.4 and bot["settings"]["search_mode"] == "HYBRID"
          and bot["settings"]["embedding_model"] == embeddings[0]["model_identifier"], json.dumps(bot["settings"]))
    expect("embedding model cannot be changed", call("PATCH", f"/api/chatbots/{created_bot}", {"settings": {"embedding_model": "something-else"}}, user), 400)
    expect("cannot publish a draft", call("POST", f"/api/chatbots/{created_bot}/publish", token=user), 409)
    expect("deleting needs the name retyped", call("DELETE", f"/api/chatbots/{created_bot}", token=user), 400)

    print("\nKnowledge base -> PENDING -> channel -> publish")
    payload, head = multipart("file", f"policy-{run}.txt", b"Refunds are accepted within 30 days with proof of purchase.\n\n" * 20)
    upload = call("POST", f"/api/chatbots/{created_bot}/documents", raw=payload, token=user, headers=head)
    check("document uploaded", upload[0] == 201 and upload[1]["status"] == "QUEUED", json.dumps(upload[1])[:200])
    dup_payload, dup_head = multipart("file", f"POLICY-{run}.txt", b"A different text with the same file name.")
    expect("same file name asks for Replace or Skip", call("POST", f"/api/chatbots/{created_bot}/documents", raw=dup_payload, token=user, headers=dup_head), 409, code="DUPLICATE_NAME")
    for _ in range(40):
        docs = call("GET", f"/api/chatbots/{created_bot}/documents", token=user)[1]
        if docs and docs[0]["status"] == "READY":
            break
        time.sleep(1.5)
    check("simulated ingestion reaches READY", bool(docs) and docs[0]["status"] == "READY", json.dumps(docs)[:200])
    check("a READY document moves the chatbot to PENDING", call("GET", f"/api/chatbots/{created_bot}", token=user)[1]["status"] == "PENDING")
    expect("publish needs a channel", call("POST", f"/api/chatbots/{created_bot}/publish", token=user), 409, code="CHECKLIST")
    expect("create public link channel", call("POST", f"/api/chatbots/{created_bot}/channels", {"channel": "PUBLIC_LINK"}, user), 201)
    check("checklist is ready", call("GET", f"/api/chatbots/{created_bot}/checklist", token=user)[1]["ready"] is True)
    expect("publish", call("POST", f"/api/chatbots/{created_bot}/publish", token=user), 200, status="ACTIVE")
    expect("pause", call("POST", f"/api/chatbots/{created_bot}/pause", token=user), 200, status="PAUSED")
    expect("resume", call("POST", f"/api/chatbots/{created_bot}/resume", token=user), 200, status="ACTIVE")
    listed = call("GET", f"/api/chatbots?search={run}&status=ACTIVE&sort=NAME_ASC", token=user)[1]
    check("list filters by search and status", len(listed) == 1 and listed[0]["kb_status"] == "READY" and listed[0]["channel_count"] == 1, json.dumps(listed)[:200])

    print("\nAdmin moderation, appeals and the audit log")
    expect("only admins can disable", call("POST", f"/api/admin/chatbots/{created_bot}/disable", {"reason": "Spam"}, user), 403)
    expect("a reason is required", call("POST", f"/api/admin/chatbots/{created_bot}/disable", {"reason": "  "}, admin), 422)
    expect("admin disables the chatbot", call("POST", f"/api/admin/chatbots/{created_bot}/disable", {"reason": "Spam content"}, admin), 200)
    owner_view = call("GET", f"/api/chatbots/{created_bot}", token=user)[1]
    check("owner sees DISABLED and the reason", owner_view["status"] == "DISABLED" and owner_view["disabled_reason"] == "Spam content", json.dumps(owner_view)[:200])
    expect("owner cannot resume a disabled chatbot", call("POST", f"/api/chatbots/{created_bot}/resume", token=user), 403, code="DISABLED")
    appeal = expect("owner appeals", call("POST", f"/api/chatbots/{created_bot}/appeals", {"message": "It was a mistake."}, user), 201, status="PENDING")
    expect("only one pending appeal", call("POST", f"/api/chatbots/{created_bot}/appeals", {"message": "again"}, user), 409)
    check("pending appeals counted for the admin", call("GET", "/api/admin/appeals/pending-count", token=admin)[1]["count"] >= 1)
    expect("rejecting needs a reason", call("POST", f"/api/admin/appeals/{appeal['id']}/reject", {"reason": ""}, admin), 422)
    expect("admin rejects", call("POST", f"/api/admin/appeals/{appeal['id']}/reject", {"reason": "Still violates policy"}, admin), 200, status="REJECTED")
    second = expect("owner appeals again", call("POST", f"/api/chatbots/{created_bot}/appeals", {"message": "Content removed."}, user), 201)
    expect("admin approves", call("POST", f"/api/admin/appeals/{second['id']}/approve", {"reason": "Looks fine now"}, admin), 200, status="APPROVED")
    check("approved appeal returns the chatbot as PAUSED", call("GET", f"/api/chatbots/{created_bot}", token=user)[1]["status"] == "PAUSED")
    expect("owner resumes", call("POST", f"/api/chatbots/{created_bot}/resume", token=user), 200, status="ACTIVE")
    logs = call("GET", f"/api/admin/audit-logs?target={bot_name.replace(' ', '%20')}", token=admin)[1]
    actions = sorted(entry["action"] for entry in logs)
    check("audit log has disable, reject and approve", actions == ["APPROVE_APPEAL", "FORCE_DISABLE_CHATBOT", "REJECT_APPEAL"], str(actions))
    check("audit entries keep the reason and the admin", any(e["reason"] == "Spam content" and e["admin_email"] == "admin@gmail.com" for e in logs))
    expect("ordinary users cannot read the audit log", call("GET", "/api/admin/audit-logs", token=user), 403)

    print("\nSuspension revokes tokens everywhere; quota is enforced")
    expect("admin suspends the user", call("POST", f"/api/admin/users/{created_user}/suspend", {"reason": "Smoke test"}, admin), 200, status="SUSPENDED")
    time.sleep(6)  # the other services cache a token verdict for at most 5 seconds
    check("suspended user's token is rejected by chatbot-service", call("GET", "/api/chatbots", token=user)[0] == 401)
    expect("suspended user cannot sign in", call("POST", "/api/auth/login", {"email": email, "password": password}), 403, code="SUSPENDED")
    expect("admin reactivates", call("POST", f"/api/admin/users/{created_user}/reactivate", {"reason": "Smoke test done"}, admin), 200, status="ACTIVE")
    user = login(email, password)
    expect("admin lowers the quota to 1 bot", call("PUT", f"/api/admin/users/{created_user}/quota", {"max_bots": 1, "max_storage_bytes": 1048576, "reason": "trial"}, admin), 200)
    expect("second chatbot refused by the quota", call("POST", "/api/chatbots", {"name": f"Second {run}"}, user), 403, code="QUOTA_BOTS")
finally:
    print("\nCleanup")
    if created_bot:
        user = login(email, password) if created_user else None
        code, _ = call("DELETE", f"/api/chatbots/{created_bot}?confirm_name={('Smoke Bot ' + run).replace(' ', '%20')}", token=user)
        check("chatbot deleted with its name retyped", code == 204, str(code))
    if created_user:
        check("test user deleted by the admin", call("DELETE", f"/api/admin/users/{created_user}", token=admin)[0] == 200)

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
