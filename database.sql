-- =====================================================================================================
-- RAG CRAFT (V5) - DATABASE SCHEMA PROMPT
-- Copy everything between the markers below into ChatGPT. The whole prompt is inside one SQL block
-- comment, so this file is still valid (empty) SQL.
-- =====================================================================================================
/* ===== BEGIN PROMPT =====

ROLE
You are a senior PostgreSQL database architect. Write production-quality PostgreSQL 15+ DDL for the
project described below. Follow the specification exactly. Do not invent tables, columns or rules that
are not listed. If something is ambiguous, pick the safest option, and list it under "ASSUMPTIONS" at the
end instead of silently guessing.

PROJECT
RAG Craft is a platform where users create document-grounded AI chatbots. Each chatbot has its own
configuration, knowledge documents, channels (public share link, web widget, Telegram), conversations
and analytics. Resources belong to their owner and must stay isolated between users. Roles: USER
(manages own chatbots) and ADMIN (manages the platform). Guests (not signed in) chat through channels.

======================================================================================================
1. HOW MANY DATABASES / SERVICES
======================================================================================================
Write ONE script (database.sql style) that creates 8 separate PostgreSQL databases. Use psql
\connect between them. Create each database only if it does not exist (use
SELECT 'CREATE DATABASE x' WHERE NOT EXISTS (...) \gexec). Do not set a database owner.

  #  Database          Service (port)                Responsibility
  -  ----------------  ----------------------------  ---------------------------------------------------
  1  identity_db       identity-service (8081)       accounts, security, sessions, MFA, quotas, notifications, audit log
  2  catalog_db        catalog-service (8082)        LLM + embedding models, prompt templates, admin limits, LLM status
  3  chatbot_db        chatbot-service (8083)        chatbots, settings, starter questions, appeals, status history
  4  knowledge_db      knowledge-service (8084)      sources, documents, chunks, ingestion jobs, web crawl, vector cleanup
  5  conversation_db   conversation-service (8085)   playground chats, messages, citations, guest answer feedback/reports
  6  channel_db        channel-service (8086)        share link / widget / Telegram, allowed domains, retired links
  7  analytics_db      analytics-service (8087)      message event log + reporting views
  8  rag_craft         FastAPI RAG service (8000)    ONE monolithic database containing ALL of the above tables
                                                     with real foreign keys (see section 6)
  -  (no database)     api-gateway (8080)            routing only, it owns no tables
  -  (not SQL)         Qdrant                        vector store used by the FastAPI RAG service (see section 7)

RULES FOR DATABASES 1-7 (microservices)
  - Each service owns its database. NEVER create a foreign key to a table in another database.
    A reference to another service is a plain UUID column with a comment "-- ref: other_db.table(id)".
  - Foreign keys are allowed only between tables inside the same database.
  - Deleting a chatbot or user is coordinated by application code across services (each service
    deletes its own rows), so inside one database use ON DELETE CASCADE only where stated.

RULES FOR DATABASE 8 (rag_craft, FastAPI monolith)
  - Same tables and columns as 1-7, merged into one database, but cross-service UUID references become
    real foreign keys (section 6 lists every one of them with its ON DELETE rule).

======================================================================================================
2. CONVENTIONS (apply to every table in every database)
======================================================================================================
  - Primary key: id UUID PRIMARY KEY DEFAULT gen_random_uuid().
  - Every table has created_at and updated_at: TIMESTAMPTZ NOT NULL DEFAULT now().
    Create a function set_updated_at() in each database and a BEFORE UPDATE trigger on every table that
    has updated_at.
  - Names: snake_case, plural table names, constraints named explicitly:
    pk_<table>, fk_<table>_<column>, uq_<table>_<columns>, ck_<table>_<rule>, ix_<table>_<columns>.
  - Enums are VARCHAR + CHECK constraint (not PostgreSQL ENUM types), so values are easy to change.
  - Text limits are real CHECK or VARCHAR(n) limits. Use NOT NULL unless the column is marked nullable.
  - Booleans have a DEFAULT. Timestamps that mean "not yet" are nullable (used_at, revoked_at ...).
  - Add an index for every foreign key and for every column used in the listed filters/sorts.
  - Emails are stored lowercase (CHECK (email = lower(email))).
  - Comment every table (COMMENT ON TABLE) with one sentence.
  - Idempotent where possible (CREATE TABLE IF NOT EXISTS, CREATE INDEX IF NOT EXISTS).
  - Existing Java services use Flyway with V1 migrations and `ddl-auto: none`. The V5 script below is the
    TARGET schema for a FRESH empty database. Every NEW column that application code written before V5
    does not know about MUST have a DEFAULT (or be nullable) so old inserts keep working.

======================================================================================================
3. STATE MACHINES THE SCHEMA MUST SUPPORT (enforce what is possible with CHECK/trigger)
======================================================================================================
Chatbot status:  DRAFT -> PENDING -> ACTIVE <-> PAUSED ; DISABLED (set by admin only)
  DRAFT    new chatbot, no READY document yet
  PENDING  at least one document READY, waiting for the owner to publish
  ACTIVE   live on all connected channels
  PAUSED   guests see "This assistant is temporarily unavailable"
  DISABLED guests see "This chatbot has been disabled"; owner may appeal; approval returns it to PAUSED
  Deleted  guests see "This chatbot is no longer available!" (row is hard-deleted; see retired_public_ids)
Account status:  UNVERIFIED -> ACTIVE <-> SUSPENDED ; ACTIVE -> PENDING_DELETION -> (permanently deleted after 7 days)
Document status: QUEUED, UPLOADING, PROCESSING, CRAWLING, EXTRACTING, CHUNKING, INDEXING, READY, FAILED, REVIEW
Appeal status:   PENDING -> APPROVED | REJECTED   (only ONE pending appeal per chatbot)
Report status:   OPEN -> RESOLVED | DISMISSED
Channel status:  LIVE, OFF, CONNECTED, DISCONNECTED, ERROR

======================================================================================================
4. TABLES PER DATABASE
======================================================================================================
Column format:  name | type | NULL? | default | notes
"NN" = NOT NULL. Every table also has id, created_at, updated_at as in section 2 (not repeated below
unless the table lacks one of them).

------------------------------------------------------------------------------------------------------
4.1  identity_db
------------------------------------------------------------------------------------------------------
users   (one row per account)
  email                   VARCHAR(254)  NN  UNIQUE, lowercase CHECK
  password_hash           VARCHAR(255)  NN  (never plaintext)
  full_name               VARCHAR(120)  NN  default ''
  display_name            VARCHAR(120)  null
  bio                     VARCHAR(2000) null
  avatar                  TEXT          null
  theme                   VARCHAR(10)   null  CHECK IN ('light','dark')
  language                VARCHAR(5)    NN  default 'en'  CHECK IN ('en','ko','km')
  timezone                VARCHAR(64)   NN  default 'UTC'
  role                    VARCHAR(10)   NN  default 'USER'  CHECK IN ('ADMIN','USER')
  status                  VARCHAR(20)   NN  default 'ACTIVE'  CHECK IN ('UNVERIFIED','ACTIVE','SUSPENDED','PENDING_DELETION')
  is_active               BOOLEAN       NN  GENERATED ALWAYS AS (status = 'ACTIVE') STORED   (kept for old API)
  signup_method           VARCHAR(10)   NN  default 'EMAIL'  CHECK IN ('EMAIL','GOOGLE','GITHUB')
  email_verified          BOOLEAN       NN  default false
  token_version           INTEGER       NN  default 0   (incrementing it revokes all issued JWTs)
  failed_attempts         INTEGER       NN  default 0
  lock_count              INTEGER       NN  default 0   (lock = min(15, lock_count) minutes after 5 failed sign-ins)
  locked_until            TIMESTAMPTZ   null
  last_login_at           TIMESTAMPTZ   null
  suspend_reason          TEXT          null
  suspended_at            TIMESTAMPTZ   null
  suspended_by            UUID          null  FK users(id) ON DELETE SET NULL
  pending_email           VARCHAR(254)  null  (new e-mail waiting for verification), unique when not null
  deletion_requested_at   TIMESTAMPTZ   null
  CHECKs: status <> 'SUSPENDED' OR suspend_reason is not blank;
          status <> 'PENDING_DELETION' OR deletion_requested_at IS NOT NULL
  Indexes: role, status, last_login_at, created_at

organizations
  name                    VARCHAR(120)  NN

organization_members
  organization_id         UUID NN FK organizations(id) ON DELETE CASCADE
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  role                    VARCHAR(10) NN CHECK IN ('ADMIN','MEMBER')
  UNIQUE (organization_id, user_id)

password_resets
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  token_hash              VARCHAR(64) NN UNIQUE   (SHA-256 of the token, never the token)
  expires_at              TIMESTAMPTZ NN          (15-30 minutes after creation)
  used_at                 TIMESTAMPTZ null

email_verifications
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  kind                    VARCHAR(15) NN CHECK IN ('SIGNUP','CHANGE_EMAIL')
  email                   VARCHAR(254) NN   (the address being verified)
  token_hash              VARCHAR(64) NN UNIQUE
  expires_at              TIMESTAMPTZ NN
  used_at                 TIMESTAMPTZ null

quota_defaults   (exactly ONE row; enforce with a boolean primary key CHECK)
  max_bots                INTEGER NN default 5   CHECK 1..5
  max_storage_bytes       BIGINT  NN default 5368709120 (5 GiB)   CHECK 1..5368709120
  updated_by              UUID null FK users(id) ON DELETE SET NULL
  -> insert the single default row.

user_quotas   (per-user override; 1:1 with users)
  user_id                 UUID NN UNIQUE FK users(id) ON DELETE CASCADE
  max_bots                INTEGER NN CHECK 1..5
  max_storage_bytes       BIGINT  NN CHECK 1..5368709120
  updated_by              UUID null FK users(id) ON DELETE SET NULL
  (usage - bots used, storage used - is NOT stored here; it is computed from chatbot_db / knowledge_db)

user_sessions   (active devices; powers "view / log out sessions")
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  refresh_token_hash      VARCHAR(64) NN UNIQUE
  device                  VARCHAR(160) null
  user_agent              TEXT null
  ip_address              INET null
  location                VARCHAR(160) null
  remember_me             BOOLEAN NN default false
  last_active_at          TIMESTAMPTZ NN default now()
  expires_at              TIMESTAMPTZ NN
  revoked_at              TIMESTAMPTZ null
  revoked_reason          VARCHAR(25) null CHECK IN ('LOGOUT','USER_REVOKED','ADMIN_FORCE_LOGOUT','PASSWORD_RESET','ACCOUNT_SUSPENDED','MFA_RESET','EXPIRED')
  CHECK: revoked_at IS NULL OR revoked_reason IS NOT NULL
  Partial index on (user_id) WHERE revoked_at IS NULL

user_totp   (authenticator app; 1:1 with users; admins are required to have it)
  user_id                 UUID NN UNIQUE FK users(id) ON DELETE CASCADE
  secret_encrypted        TEXT NN   (encrypted at rest, never plaintext)
  confirmed_at            TIMESTAMPTZ null   (set after the first valid 6-digit code)
  last_used_step          BIGINT null        (30-second step, prevents code replay)

user_passkeys
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  credential_id           TEXT NN UNIQUE
  public_key              BYTEA NN
  sign_count              BIGINT NN default 0
  name                    VARCHAR(80) NN
  transports              TEXT[] null
  last_used_at            TIMESTAMPTZ null

linked_accounts   (Google / GitHub, USER role only)
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  provider                VARCHAR(10) NN CHECK IN ('GOOGLE','GITHUB')
  provider_user_id        VARCHAR(255) NN
  provider_email          VARCHAR(254) null
  UNIQUE (provider, provider_user_id);  UNIQUE (user_id, provider)

notifications   (simulated/real e-mail + push inbox; other services create rows through an internal API)
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  channel                 VARCHAR(5) NN CHECK IN ('EMAIL','PUSH')
  type                    VARCHAR(30) NN CHECK IN ('KB_PROCESSING_FAILED','CRAWL_FAILED','LLM_FAILED','NEAR_LIMIT',
                          'REPORT_INACCURATE','REPORT_UNHELPFUL','CHATBOT_DISABLED','APPEAL_DECIDED','ACCOUNT_SUSPENDED',
                          'ACCOUNT_REACTIVATED','PASSWORD_RESET','EMAIL_VERIFICATION','ADMIN_HIGH_ERROR_RATE',
                          'ADMIN_UNHANDLED_ERROR','ADMIN_USER_REPORT','ADMIN_LLM_UNAVAILABLE','ADMIN_DB_FAILURE',
                          'ADMIN_API_ERROR','ADMIN_NEW_APPEAL')
  title                   VARCHAR(160) NN
  body                    TEXT NN
  data                    JSONB NN default '{}'
  delivery_status         VARCHAR(10) NN default 'PENDING' CHECK IN ('PENDING','SENT','FAILED')
  delivered_at            TIMESTAMPTZ null
  read_at                 TIMESTAMPTZ null
  Index: (user_id, read_at, created_at DESC)

push_devices   (mobile companion app)
  user_id                 UUID NN FK users(id) ON DELETE CASCADE
  platform                VARCHAR(7) NN CHECK IN ('IOS','ANDROID')
  push_token              TEXT NN UNIQUE
  device_name             VARCHAR(120) null
  last_seen_at            TIMESTAMPTZ null

audit_logs   (append-only admin action log)
  admin_id                UUID null FK users(id) ON DELETE SET NULL
  admin_email             VARCHAR(254) NN   (snapshot, survives user deletion)
  action                  VARCHAR(30) NN CHECK IN ('FORCE_DISABLE_CHATBOT','REENABLE_CHATBOT','SUSPEND_USER','REACTIVATE_USER',
                          'APPROVE_APPEAL','REJECT_APPEAL','SET_QUOTA','RESET_MFA','FORCE_LOGOUT')
  target_type             VARCHAR(10) NN CHECK IN ('USER','CHATBOT','APPEAL')
  target_id               UUID NN            -- ref: users | chatbot_db.chatbots | chatbot_db.chatbot_appeals (no FK)
  target_label            VARCHAR(254) NN    (e-mail or chatbot name snapshot)
  reason                  TEXT NN CHECK (btrim(reason) <> '')
  details                 JSONB NN default '{}'   (for example {"before":{...},"after":{...}})
  source_service          VARCHAR(30) null
  MUST be read-only: add a trigger that RAISES EXCEPTION on UPDATE and DELETE (and block TRUNCATE).
  Indexes: (admin_email), (action), (target_type, target_id), (created_at DESC)

------------------------------------------------------------------------------------------------------
4.2  catalog_db
------------------------------------------------------------------------------------------------------
models
  name                    VARCHAR(120) NN
  provider                VARCHAR(20)  NN default 'OLLAMA' CHECK = 'OLLAMA'
  model_identifier        VARCHAR(200) NN UNIQUE   (exact Ollama tag, e.g. llama3.2:3b)
  kind                    VARCHAR(10)  NN default 'LLM' CHECK IN ('LLM','EMBEDDING')
  embedding_dimensions    INTEGER null   (required when kind='EMBEDDING', must be NULL for 'LLM'; used as the Qdrant vector size)
  enabled                 BOOLEAN NN default true   (only enabled models appear in the user's "allowed list")
  is_default              BOOLEAN NN default false
  is_system_fallback      BOOLEAN NN default false  (the platform-wide fallback LLM the admin can switch)
  CHECKs: NOT is_default OR enabled;  NOT is_system_fallback OR (enabled AND kind='LLM')
  Partial unique indexes: one default per kind; at most one is_system_fallback row.

system_prompt_templates
  name                    VARCHAR(120) NN
  prompt                  TEXT NN
  enabled                 BOOLEAN NN default true

advanced_setting_limits   (admin sets min / max / default for every advanced chatbot setting)
  setting_key             VARCHAR(30) NN UNIQUE CHECK IN ('temperature','max_tokens','top_k','max_context_tokens','chunk_size','chunk_overlap')
  min_value               NUMERIC(12,3) NN
  max_value               NUMERIC(12,3) NN
  default_value           NUMERIC(12,3) NN
  updated_by              UUID null   -- ref: identity_db.users(id)
  CHECK: min_value <= default_value AND default_value <= max_value
  Seed 6 rows: temperature 0/1/0.4 ; max_tokens 128/4096/1024 ; top_k 1/20/5 ;
               max_context_tokens 512/16384/4096 ; chunk_size 100/2000/500 ; chunk_overlap 0/500/50

llm_providers   (one row per provider, drives "LLM service status per provider")
  provider                VARCHAR(20) NN UNIQUE
  status                  VARCHAR(12) NN default 'OPERATIONAL' CHECK IN ('OPERATIONAL','DEGRADED','DOWN')
  status_message          TEXT null
  checked_at              TIMESTAMPTZ null
  updated_by              UUID null   -- ref: identity_db.users(id)

llm_status_events   (history / error alerts)
  provider                VARCHAR(20) NN FK llm_providers(provider) ON DELETE CASCADE
  from_status             VARCHAR(12) null
  to_status               VARCHAR(12) NN
  message                 TEXT null

SEED DATA for catalog_db: models  Llama 3.2 3B (llama3.2:3b, LLM, default), Qwen (qwen2.5:3b, LLM, system fallback),
  nomic-embed-text (EMBEDDING, 768 dims, default), bge-m3 (EMBEDDING, 1024 dims); one prompt template
  "Helpful assistant" = 'Answer using the provided knowledge.'; the 6 advanced_setting_limits rows;
  llm_providers row OLLAMA / OPERATIONAL. (Comment that identifiers must match `ollama list`.)

------------------------------------------------------------------------------------------------------
4.3  chatbot_db
------------------------------------------------------------------------------------------------------
chatbots
  organization_id         UUID null   -- ref: identity_db.organizations(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  name                    VARCHAR(50) NN CHECK (btrim(name) <> '')
  description             VARCHAR(200) NN default ''
  avatar                  TEXT null
  status                  VARCHAR(10) NN default 'DRAFT' CHECK IN ('DRAFT','PENDING','ACTIVE','PAUSED','DISABLED')
  disabled_reason         TEXT null
  disabled_at             TIMESTAMPTZ null
  disabled_by             UUID null   -- ref: identity_db.users(id) (an ADMIN)
  CHECK: status <> 'DISABLED' OR (disabled_reason is not blank AND disabled_at IS NOT NULL)
  Indexes: owner_id, status, (owner_id, status), created_at

chatbot_starter_questions   (max 4, ordered; replaces the old JSON text column)
  chatbot_id              UUID NN FK chatbots(id) ON DELETE CASCADE
  position                SMALLINT NN CHECK BETWEEN 1 AND 4
  question                VARCHAR(300) NN CHECK (btrim(question) <> '')
  UNIQUE (chatbot_id, position)

chatbot_settings   (1:1 with chatbots)
  chatbot_id              UUID NN UNIQUE FK chatbots(id) ON DELETE CASCADE
  model_id                UUID null   -- ref: catalog_db.models(id)
  model_name              VARCHAR(200) NN   (primary model)
  fallback_model_id       UUID null   -- ref: catalog_db.models(id)
  fallback_model_name     VARCHAR(200) null (auto-switch when the primary provider fails); CHECK differs from model_name
  embedding_model         VARCHAR(200) NN   (chosen at creation; IMMUTABLE -> add a BEFORE UPDATE trigger that rejects changes)
  prompt_template_id      UUID null   -- ref: catalog_db.system_prompt_templates(id)
  system_instruction      TEXT NN
  custom_instruction      TEXT null
  tone                    VARCHAR(20) NN default 'PROFESSIONAL' CHECK IN ('PROFESSIONAL','FRIENDLY','CASUAL','FORMAL','CUSTOM')
  answer_length           VARCHAR(10) NN default 'CONCISE' CHECK IN ('CONCISE','DETAILED')
  formatting              VARCHAR(5)  NN default 'RICH' CHECK IN ('RICH','PLAIN')
  language                VARCHAR(4)  NN default 'AUTO' CHECK IN ('AUTO','EN','KM','KO')
  temperature             DOUBLE PRECISION NN default 0.4 CHECK 0..1
  max_tokens              INTEGER NN default 1024 CHECK > 0
  top_k                   INTEGER NN default 5 CHECK 1..20
  search_mode             VARCHAR(10) NN default 'HYBRID' CHECK IN ('SEMANTIC','KEYWORD','HYBRID')
  max_context_tokens      INTEGER NN default 4096 CHECK > 0
  chunk_size              INTEGER NN default 500 CHECK > 0
  chunk_overlap           INTEGER NN default 50 CHECK >= 0 AND < chunk_size
  answer_from_documents_only BOOLEAN NN default true
  show_citations          BOOLEAN NN default true   (= "require citations")
  welcome_message         VARCHAR(2000) null
  fallback_message        VARCHAR(2000) null  (optional override; NULL = use the localized default
                          "Sorry, I couldn't find that in my knowledge base.")
  CHECK: tone <> 'CUSTOM' OR custom_instruction is not blank
  NOTE (comment in script): the min/max values above are absolute; the admin's tighter limits live in
  catalog_db.advanced_setting_limits and are enforced by the application.

chatbot_appeals
  chatbot_id              UUID NN FK chatbots(id) ON DELETE CASCADE
  owner_id                UUID NN     -- ref: identity_db.users(id)
  message                 VARCHAR(500) NN CHECK (btrim(message) <> '')
  status                  VARCHAR(10) NN default 'PENDING' CHECK IN ('PENDING','APPROVED','REJECTED')
  decision_reason         TEXT null
  decided_by              UUID null   -- ref: identity_db.users(id)
  decided_at              TIMESTAMPTZ null
  CHECKs: status = 'PENDING' OR decided_at IS NOT NULL;  status <> 'REJECTED' OR decision_reason is not blank
  Partial UNIQUE index on (chatbot_id) WHERE status = 'PENDING'   -> only one pending appeal per chatbot
  Index: (status, created_at), owner_id

chatbot_status_history
  chatbot_id              UUID NN FK chatbots(id) ON DELETE CASCADE
  from_status             VARCHAR(10) null
  to_status               VARCHAR(10) NN
  changed_by              UUID null   -- ref: identity_db.users(id); NULL when the system changed it
  actor_role              VARCHAR(6) NN CHECK IN ('OWNER','ADMIN','SYSTEM')
  reason                  TEXT null
  (no updated_at needed; append-only, but keep the column for convention consistency)

------------------------------------------------------------------------------------------------------
4.4  knowledge_db
------------------------------------------------------------------------------------------------------
knowledge_sources   (one row per document or website)
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  source_type             VARCHAR(10) NN default 'DOCUMENT' CHECK IN ('DOCUMENT','WEBSITE')
  source_key              VARCHAR(512) NN   (file checksum, or the URL for websites)
  url                     VARCHAR(2048) null
  UNIQUE (chatbot_id, source_type, source_key)

documents   (1:1 with knowledge_sources)
  source_id               UUID NN UNIQUE FK knowledge_sources(id) ON DELETE CASCADE
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  name                    VARCHAR(255) NN
  file_type               VARCHAR(10) NN CHECK IN ('PDF','DOCX','TXT','MD','XLSX','WEBSITE')
  mime_type               VARCHAR(120) NN
  size_bytes              BIGINT NN CHECK > 0;  CHECK (file_type = 'WEBSITE' OR size_bytes <= 20971520)  (20 MB limit)
  storage_key             VARCHAR(255) null UNIQUE
  content_hash            CHAR(64) null   (SHA-256, to detect duplicates)
  status                  VARCHAR(12) NN default 'QUEUED' CHECK IN (QUEUED, UPLOADING, PROCESSING, CRAWLING, EXTRACTING,
                          CHUNKING, INDEXING, READY, FAILED, REVIEW)
  error_message           VARCHAR(2000) null  (CHECK: status <> 'FAILED' OR error_message IS NOT NULL)
  chunk_count             INTEGER NN default 0 CHECK >= 0
  embedding_model         VARCHAR(200) null   (model used when indexed)
  chunk_size              INTEGER null
  chunk_overlap           INTEGER null
  retry_count             INTEGER NN default 0
  processing_started_at   TIMESTAMPTZ null
  indexed_at              TIMESTAMPTZ null
  UNIQUE index on (chatbot_id, lower(name))  -> duplicate detection (the app then offers Replace or Skip)
  Indexes: chatbot_id, owner_id, status, content_hash, (chatbot_id, status), (chatbot_id, file_type)

document_chunks
  document_id             UUID NN FK documents(id) ON DELETE CASCADE
  chunk_index             INTEGER NN
  page                    INTEGER null
  sheet                   VARCHAR(120) null
  row_number              INTEGER null
  content                 TEXT NN
  token_count             INTEGER null
  vector_id               UUID null   (Qdrant point id, used for cleanup)
  content_tsv             tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED
  UNIQUE (document_id, chunk_index)
  GIN index on content_tsv  (keyword / hybrid search; 'simple' config so Khmer and Korean work)

ingestion_jobs
  document_id             UUID NN FK documents(id) ON DELETE CASCADE
  status                  VARCHAR(12) NN CHECK (same value list as documents.status)
  attempts                INTEGER NN default 0
  error_message           VARCHAR(2000) null
  started_at / finished_at TIMESTAMPTZ null

crawl_jobs   (site crawl: preview first, import second)
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  start_url               VARCHAR(2048) NN
  max_depth               SMALLINT NN CHECK BETWEEN 1 AND 3
  max_pages               SMALLINT NN CHECK BETWEEN 50 AND 100
  status                  VARCHAR(10) NN default 'PREVIEW' CHECK IN ('PREVIEW','IMPORTING','COMPLETED','FAILED','CANCELLED')
  error_message           TEXT null

crawl_pages
  crawl_job_id            UUID NN FK crawl_jobs(id) ON DELETE CASCADE
  url                     VARCHAR(2048) NN
  title                   VARCHAR(500) null
  content_preview         TEXT null
  depth                   SMALLINT NN default 0
  status                  VARCHAR(8) NN CHECK IN ('OK','EMPTY','BLOCKED','FAILED')
  selected                BOOLEAN NN default true
  document_id             UUID null FK documents(id) ON DELETE SET NULL   (set once imported)
  UNIQUE (crawl_job_id, url)

vector_cleanup_jobs   (NO foreign keys on purpose: the task must outlive the deleted rows)
  owner_id                UUID NN
  chatbot_id              UUID NN
  document_id             UUID null
  attempts                INTEGER NN default 0
  error_message           TEXT null
  next_attempt_at         TIMESTAMPTZ NN default now()   (index)

VIEW v_owner_storage_usage:  owner_id, SUM(size_bytes) AS bytes_used, COUNT(*) AS documents  (from documents)

------------------------------------------------------------------------------------------------------
4.5  conversation_db
------------------------------------------------------------------------------------------------------
conversations   (PLAYGROUND chats only; guest conversations are NOT stored)
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  channel                 VARCHAR(12) NN default 'PLAYGROUND' CHECK = 'PLAYGROUND'  (CHECK IN the 4 channels would also be
                          accepted by old code, but V5 forbids storing guest chats, so use = 'PLAYGROUND' and comment it)
  integration_id          UUID null   -- ref: channel_db.channel_integrations(id)  (kept for compatibility, always NULL)
  session_hash            VARCHAR(64) null (kept for compatibility, always NULL)
  title                   VARCHAR(200) NN default 'New conversation'
  Indexes: (chatbot_id, updated_at DESC), owner_id

messages
  conversation_id         UUID NN FK conversations(id) ON DELETE CASCADE
  sequence                INTEGER NN
  role                    VARCHAR(10) NN CHECK IN ('USER','ASSISTANT')
  content                 TEXT NN
  model_name              VARCHAR(200) null
  tokens_in / tokens_out  INTEGER null
  latency_ms              INTEGER null
  unanswered              BOOLEAN NN default false  (assistant answered "not found")
  UNIQUE (conversation_id, sequence)

message_sources   (citations)
  message_id              UUID NN FK messages(id) ON DELETE CASCADE
  document_id             UUID null   -- ref: knowledge_db.documents(id)   (kept after the document is deleted)
  chunk_id                UUID null   -- ref: knowledge_db.document_chunks(id)
  document_name           VARCHAR(255) NN (snapshot)
  excerpt                 TEXT NN
  page INTEGER null, sheet VARCHAR(120) null, row_number INTEGER null, chunk_index INTEGER null
  score                   DOUBLE PRECISION NN
  url                     VARCHAR(2048) null

answer_feedback   (guest rating OR report; ONE feedback per answer)
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  channel                 VARCHAR(12) NN CHECK IN ('PUBLIC_LINK','WEB_WIDGET','TELEGRAM')
  integration_id          UUID null   -- ref: channel_db.channel_integrations(id)
  answer_ref              VARCHAR(64) NN UNIQUE   (id of the guest-side answer -> enforces one feedback per answer)
  kind                    VARCHAR(6) NN CHECK IN ('RATING','REPORT')
  rating                  VARCHAR(11) null CHECK IN ('HELPFUL','NOT_HELPFUL')
  reason                  VARCHAR(10) null CHECK IN ('INACCURATE','UNHELPFUL')
  comment                 VARCHAR(500) null
  question                TEXT null      (stored ONLY for REPORT)
  answer                  TEXT null      (stored ONLY for REPORT)
  sources                 JSONB null     (documents/chunks used, for admin review; REPORT only)
  status                  VARCHAR(9) null CHECK IN ('OPEN','RESOLVED','DISMISSED')
  handled_by              UUID null   -- ref: identity_db.users(id)
  handled_at              TIMESTAMPTZ null
  CHECK for RATING:  rating NOT NULL and reason, comment, question, answer, sources, status all NULL
  CHECK for REPORT:  reason, question, answer, status NOT NULL and rating NULL
  CHECK: status = 'OPEN' OR handled_at IS NOT NULL   (when status is not null)
  Indexes: (chatbot_id, status, created_at DESC), (chatbot_id, reason), (kind)

------------------------------------------------------------------------------------------------------
4.6  channel_db
------------------------------------------------------------------------------------------------------
channel_integrations
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  channel                 VARCHAR(12) NN CHECK IN ('PUBLIC_LINK','WEB_WIDGET','TELEGRAM')
  public_id               VARCHAR(64) NN UNIQUE   (token used in /share/{slug}/{token} and /widget/{token})
  enabled                 BOOLEAN NN default false
  status                  VARCHAR(12) NN default 'OFF' CHECK IN ('LIVE','OFF','CONNECTED','DISCONNECTED','ERROR')
  error_reason            TEXT null   (CHECK: status <> 'ERROR' OR error_reason IS NOT NULL)
  settings                TEXT null   (JSON text: title, welcome, color, position, icon, starters - the Java code stores JSON as text)
  expires_at              TIMESTAMPTZ null   (optional public link expiry)
  access_password_hash    VARCHAR(255) null  (optional link password, HASHED, never plaintext)
  regenerated_at          TIMESTAMPTZ null
  credential              TEXT null          (Telegram bot token, ENCRYPTED)
  token_mask              VARCHAR(40) null   (display only, e.g. 1234...WXYZ)
  webhook_secret          VARCHAR(64) null
  telegram_bot_id         VARCHAR(40) null   UNIQUE where not null
  telegram_username       VARCHAR(120) null
  UNIQUE (chatbot_id, channel);  Indexes: chatbot_id, owner_id, status

channel_allowed_domains   (web widget "allowed domains" list)
  integration_id          UUID NN FK channel_integrations(id) ON DELETE CASCADE
  domain                  VARCHAR(253) NN CHECK (domain = lower(domain) AND domain ~ '^([a-z0-9-]+\.)*[a-z0-9-]+(:[0-9]+)?$')
  UNIQUE (integration_id, domain)

retired_public_ids   (so a guest who opens an old link sees the right message)
  public_id               VARCHAR(64) NN UNIQUE
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  reason                  VARCHAR(16) NN CHECK IN ('CHATBOT_DELETED','REGENERATED','CHANNEL_REMOVED')
  retired_at              TIMESTAMPTZ NN default now()
  (CHATBOT_DELETED -> guest sees "This chatbot is no longer available!")

telegram_updates   (idempotency for Telegram webhook retries)
  integration_id          UUID NN FK channel_integrations(id) ON DELETE CASCADE
  update_id               BIGINT NN
  answer                  TEXT null
  sent_parts              INTEGER NN default 0
  UNIQUE (integration_id, update_id)

channel_messages   (one row per guest exchange, used for "messages this week" per channel)
  integration_id          UUID NN FK channel_integrations(id) ON DELETE CASCADE
  Index: (integration_id, created_at)

------------------------------------------------------------------------------------------------------
4.7  analytics_db
------------------------------------------------------------------------------------------------------
analytics_events   (one row per question/answer exchange; messages default 2)
  owner_id                UUID NN     -- ref: identity_db.users(id)
  chatbot_id              UUID NN     -- ref: chatbot_db.chatbots(id)
  channel                 VARCHAR(12) NN CHECK IN ('PLAYGROUND','PUBLIC_LINK','WEB_WIDGET','TELEGRAM')
  messages                INTEGER NN default 2
  occurred_at             TIMESTAMPTZ NN
  session_id              VARCHAR(64) null   (a chat session)
  guest_id                VARCHAR(64) null   (anonymous guest id, no personal data)
  question                TEXT null          (needed for "most asked" / "unanswered"; comment the privacy implication)
  question_hash           CHAR(64) null      (SHA-256 of lower(btrim(question)) to group questions)
  unanswered              BOOLEAN NN default false
  tokens_in               INTEGER NN default 0
  tokens_out              INTEGER NN default 0
  model_name              VARCHAR(200) null
  latency_ms              INTEGER null
  Indexes: (owner_id, occurred_at), (chatbot_id, occurred_at), (occurred_at), (chatbot_id, session_id),
           partial (chatbot_id, question_hash) WHERE unanswered, (chatbot_id, question_hash)

VIEWS (write them):
  v_chatbot_daily      day, chatbot_id, owner_id, channel, messages, sessions (distinct session_id), guests (distinct guest_id),
                       unanswered_count, tokens
  v_owner_token_usage  owner_id, month, tokens (tokens_in + tokens_out)   -> "top users by token usage"
  v_platform_daily     day, messages, active_chatbots, tokens                -> admin "daily message volume"
  (Active chat sessions = sessions with an event in the last 30 minutes; document that as a query comment.)

======================================================================================================
5. RELATIONSHIP TABLES
======================================================================================================
5.1  Foreign keys INSIDE each microservice database (real FKs)
  identity_db:     organization_members.organization_id -> organizations.id            (CASCADE)
                   organization_members.user_id         -> users.id                    (CASCADE)
                   password_resets.user_id              -> users.id                    (CASCADE)
                   email_verifications.user_id          -> users.id                    (CASCADE)
                   user_quotas.user_id                  -> users.id                    (CASCADE, 1:1)
                   user_sessions.user_id                -> users.id                    (CASCADE)
                   user_totp.user_id                    -> users.id                    (CASCADE, 1:1)
                   user_passkeys.user_id                -> users.id                    (CASCADE)
                   linked_accounts.user_id              -> users.id                    (CASCADE)
                   notifications.user_id                -> users.id                    (CASCADE)
                   push_devices.user_id                 -> users.id                    (CASCADE)
                   users.suspended_by                   -> users.id                    (SET NULL)
                   quota_defaults.updated_by            -> users.id                    (SET NULL)
                   user_quotas.updated_by               -> users.id                    (SET NULL)
                   audit_logs.admin_id                  -> users.id                    (SET NULL)
  catalog_db:      llm_status_events.provider           -> llm_providers.provider      (CASCADE)
  chatbot_db:      chatbot_settings.chatbot_id          -> chatbots.id                 (CASCADE, 1:1)
                   chatbot_starter_questions.chatbot_id -> chatbots.id                 (CASCADE)
                   chatbot_appeals.chatbot_id           -> chatbots.id                 (CASCADE)
                   chatbot_status_history.chatbot_id    -> chatbots.id                 (CASCADE)
  knowledge_db:    documents.source_id                  -> knowledge_sources.id        (CASCADE, 1:1)
                   document_chunks.document_id          -> documents.id                (CASCADE)
                   ingestion_jobs.document_id           -> documents.id                (CASCADE)
                   crawl_pages.crawl_job_id             -> crawl_jobs.id               (CASCADE)
                   crawl_pages.document_id              -> documents.id                (SET NULL)
  conversation_db: messages.conversation_id             -> conversations.id            (CASCADE)
                   message_sources.message_id           -> messages.id                 (CASCADE)
  channel_db:      channel_allowed_domains.integration_id -> channel_integrations.id   (CASCADE)
                   telegram_updates.integration_id      -> channel_integrations.id     (CASCADE)
                   channel_messages.integration_id       -> channel_integrations.id    (CASCADE)
  analytics_db:    (none)

5.2  Cardinality summary
  organizations 1--N organization_members N--1 users
  users 1--1 user_quotas | 1--1 user_totp | 1--N user_sessions | 1--N user_passkeys | 1--N linked_accounts
  users 1--N notifications | 1--N push_devices | 1--N password_resets | 1--N email_verifications | 1--N audit_logs(as admin)
  chatbots 1--1 chatbot_settings | 1--N(max 4) chatbot_starter_questions | 1--N chatbot_appeals (max 1 PENDING) | 1--N chatbot_status_history
  knowledge_sources 1--1 documents 1--N document_chunks | 1--N ingestion_jobs ; crawl_jobs 1--N crawl_pages N--0..1 documents
  conversations 1--N messages 1--N message_sources
  channel_integrations 1--N channel_allowed_domains | 1--N telegram_updates | 1--N channel_messages
  llm_providers 1--N llm_status_events

5.3  Cross-service references (NO foreign key; UUID + comment). Include this as a table comment block in the script.
  From (database.table.column)                              To (database.table.column)
  chatbot_db.chatbots.owner_id                              identity_db.users.id
  chatbot_db.chatbots.organization_id                       identity_db.organizations.id
  chatbot_db.chatbots.disabled_by                           identity_db.users.id
  chatbot_db.chatbot_settings.model_id / fallback_model_id  catalog_db.models.id
  chatbot_db.chatbot_settings.prompt_template_id            catalog_db.system_prompt_templates.id
  chatbot_db.chatbot_appeals.owner_id / decided_by          identity_db.users.id
  chatbot_db.chatbot_status_history.changed_by              identity_db.users.id
  catalog_db.advanced_setting_limits.updated_by             identity_db.users.id
  catalog_db.llm_providers.updated_by                       identity_db.users.id
  knowledge_db.knowledge_sources.chatbot_id                 chatbot_db.chatbots.id
  knowledge_db.knowledge_sources.owner_id                   identity_db.users.id
  knowledge_db.documents.chatbot_id / owner_id              chatbot_db.chatbots.id / identity_db.users.id
  knowledge_db.crawl_jobs.chatbot_id / owner_id             chatbot_db.chatbots.id / identity_db.users.id
  knowledge_db.vector_cleanup_jobs.*                        (deliberately none)
  conversation_db.conversations.chatbot_id / owner_id       chatbot_db.chatbots.id / identity_db.users.id
  conversation_db.conversations.integration_id              channel_db.channel_integrations.id
  conversation_db.message_sources.document_id / chunk_id    knowledge_db.documents.id / document_chunks.id
  conversation_db.answer_feedback.chatbot_id / owner_id     chatbot_db.chatbots.id / identity_db.users.id
  conversation_db.answer_feedback.integration_id            channel_db.channel_integrations.id
  conversation_db.answer_feedback.handled_by                identity_db.users.id
  channel_db.channel_integrations.chatbot_id / owner_id     chatbot_db.chatbots.id / identity_db.users.id
  channel_db.retired_public_ids.chatbot_id / owner_id       chatbot_db.chatbots.id / identity_db.users.id
  analytics_db.analytics_events.chatbot_id / owner_id       chatbot_db.chatbots.id / identity_db.users.id
  identity_db.audit_logs.target_id                          users.id | chatbot_db.chatbots.id | chatbot_db.chatbot_appeals.id (by target_type)

======================================================================================================
6. DATABASE 8: rag_craft  (FastAPI monolith, ONE database, real foreign keys)
======================================================================================================
Create every table from sections 4.1 - 4.7 in this single database (same columns, constraints, indexes,
triggers, views, seed data), EXCEPT:
  - Omit stored-only-for-microservice details that make no sense in one DB: none are removed; keep all.
  - Add these tables that exist only in the FastAPI backend:
      api_keys   organization_id UUID NN FK organizations(id) ON DELETE CASCADE,
                 name VARCHAR(120) NN, key_hash VARCHAR(255) NN UNIQUE, expires_at TIMESTAMPTZ null, revoked_at TIMESTAMPTZ null
  - audit_logs: use the V5 shape from 4.1 (admin_id, admin_email, action, target_type, target_id, target_label,
      reason, details, created_at) PLUS the two legacy columns organization_id (FK organizations ON DELETE SET NULL)
      and resource_type VARCHAR(80) null, so the older FastAPI code that writes (organization_id, user_id,
      action, resource_type, resource_id) can be migrated. Remove the CHECK on action values for this one table
      (FastAPI also writes actions like 'chatbot.created'); keep it append-only.
  - models.model_identifier is VARCHAR(120) in the FastAPI code; use VARCHAR(200) like catalog_db.
  - messages/conversations: same V5 rule (PLAYGROUND only).
Replace every cross-service reference of section 5.3 with a REAL foreign key:
  chatbots.owner_id                      -> users.id                      ON DELETE RESTRICT
  chatbots.organization_id               -> organizations.id              ON DELETE CASCADE  (NOT NULL here)
  chatbots.disabled_by                   -> users.id                      ON DELETE SET NULL
  chatbot_settings.model_id              -> models.id                     ON DELETE RESTRICT
  chatbot_settings.fallback_model_id     -> models.id                     ON DELETE SET NULL
  chatbot_settings.prompt_template_id    -> system_prompt_templates.id    ON DELETE SET NULL
  chatbot_appeals.owner_id / decided_by  -> users.id                      ON DELETE CASCADE / SET NULL
  chatbot_status_history.changed_by      -> users.id                      ON DELETE SET NULL
  knowledge_sources.chatbot_id           -> chatbots.id                   ON DELETE CASCADE
  documents.chatbot_id                   -> chatbots.id                   ON DELETE CASCADE
  documents.owner_id                     -> users.id                      ON DELETE CASCADE
  crawl_jobs.chatbot_id / owner_id       -> chatbots.id / users.id        ON DELETE CASCADE
  conversations.chatbot_id               -> chatbots.id                   ON DELETE CASCADE
  conversations.owner_id                 -> users.id                      ON DELETE CASCADE
  conversations.integration_id           -> channel_integrations.id       ON DELETE SET NULL
  message_sources.chunk_id               -> document_chunks.id            ON DELETE SET NULL
  message_sources.document_id            (plain UUID, NO FK: citations must survive document deletion)
  answer_feedback.chatbot_id             -> chatbots.id                   ON DELETE CASCADE
  answer_feedback.integration_id         -> channel_integrations.id       ON DELETE SET NULL
  answer_feedback.handled_by             -> users.id                      ON DELETE SET NULL
  channel_integrations.chatbot_id        -> chatbots.id                   ON DELETE CASCADE
  retired_public_ids.chatbot_id          (plain UUID, NO FK: the chatbot row is already deleted)
  analytics_events.chatbot_id            -> chatbots.id                   ON DELETE CASCADE
  analytics_events.owner_id              -> users.id                      ON DELETE CASCADE
  advanced_setting_limits.updated_by, llm_providers.updated_by            -> users.id ON DELETE SET NULL
  vector_cleanup_jobs                    (NO foreign keys, same as before)
Also add in this database a CHECK-free helper view:
  v_owner_quota_usage: user_id, max_bots, bots_used (COUNT chatbots), max_storage_bytes, storage_used (SUM documents.size_bytes)
  (using user_quotas LEFT JOIN quota_defaults for users without an override).

======================================================================================================
7. QDRANT (document only, do not write SQL)
======================================================================================================
Add a comment block at the end of the script describing the Qdrant collection used by the FastAPI RAG
service: one collection (name from env QDRANT_COLLECTION, default rag_craft); vector size =
models.embedding_dimensions of the chatbot's embedding model; distance Cosine; point id =
document_chunks.vector_id; payload keys: chatbot_id, owner_id, document_id, chunk_id, chunk_index, page,
sheet, row_number, embedding_model. Every search MUST filter by chatbot_id AND owner_id AND the
chatbot's embedding_model, then be re-checked against Postgres (document status READY).

======================================================================================================
8. APPLICATION RULES THE SCHEMA ASSUMES (list them as comments at the top of the script)
======================================================================================================
  - Guest conversations are never stored. Only a REPORT keeps the question and the answer.
  - A user can have at most max_bots chatbots and max_storage_bytes of documents (limits in user_quotas /
    quota_defaults, usage computed). Admin can set quota up to 5 bots and 5 GiB.
  - The chatbot embedding model cannot change after creation.
  - Admin limits for advanced settings (catalog_db.advanced_setting_limits) are enforced by the application.
  - Every admin action writes one audit_logs row with a mandatory reason.
  - A suspended / pending-deletion account takes all its published chatbots offline.
  - Deleting an account is permanent after 7 days (deletion_requested_at + 7 days).
  - Authentication: this schema assumes identity-service owns passwords, sessions, MFA and linked accounts.
    The product spec mentions Keycloak. If Keycloak is used instead, Keycloak keeps its own database and
    identity_db keeps ONLY: users (profile mirror keyed by the Keycloak subject, without password_hash,
    token_version, failed_attempts, lock_count, locked_until, email_verified), organizations,
    organization_members, user_quotas, quota_defaults, notifications, push_devices, audit_logs.
    Mark the tables/columns that would be dropped with a comment "-- KEYCLOAK: drop if Keycloak is used".

======================================================================================================
9. WHAT YOU MUST OUTPUT
======================================================================================================
  1. ONE SQL script, in this order: header comments (rules from section 8) -> create databases ->
     for each database: \connect, extension (CREATE EXTENSION IF NOT EXISTS pgcrypto only if needed),
     set_updated_at(), tables in dependency order, indexes, triggers, views, seed data, COMMENT ON TABLE.
  2. A relationship summary comment block at the end of each database (copy of 5.1 for that database).
  3. A section "ASSUMPTIONS" listing every decision you had to make.
  4. A section "APPLICATION CHANGES REQUIRED" listing what the Java (JPA entities) and FastAPI
     (SQLAlchemy models) code must change to match, in particular: is_active becoming a generated column,
     starter_questions moving from a JSON text column to chatbot_starter_questions, guest conversations
     no longer stored, new NOT NULL-with-default columns.
  5. A verification checklist of 15 SQL queries (information_schema / pg_constraint) that prove: every
     table has a primary key, no cross-database foreign key exists in databases 1-7, the partial unique
     indexes exist, the CHECK constraints exist, and the audit_logs trigger blocks UPDATE and DELETE.
  Do not output anything outside these sections. Do not shorten the DDL with "..." or "etc.".
  Validate your own script mentally for PostgreSQL 15 syntax before answering (generated columns,
  partial indexes, CHECK constraints, trigger functions, \gexec usage).

===== END PROMPT ===== */
