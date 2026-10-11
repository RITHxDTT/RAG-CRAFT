-- RAG Craft V5 schema for database craftrag_db (PostgreSQL 16).
-- One PostgreSQL schema per service. The microservice schemas have NO cross-schema foreign keys (a reference to another
-- service is a plain UUID, marked '-- ref:' in the table comment). The `fastapi` schema is the monolith with real foreign keys.
-- Point each Spring service at its schema with  jdbc:postgresql://HOST:5432/craftrag_db?currentSchema=<schema>  (Flyway disabled).
-- Rules assumed by the schema: guest conversations are never stored (only reports keep question + answer); the chatbot embedding
-- model cannot change after creation; admin actions are written to the append-only identity.audit_logs with a mandatory reason.

BEGIN;

-- ====================================================================================================
-- SCHEMA identity: accounts, security, sessions, MFA, quotas, notifications, audit log (identity-service, 8081)
-- ====================================================================================================
CREATE SCHEMA identity;
SET LOCAL search_path TO identity, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE users (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL DEFAULT '',
    display_name VARCHAR(120),
    bio VARCHAR(2000),
    avatar TEXT,
    theme VARCHAR(10),
    language VARCHAR(5) NOT NULL DEFAULT 'en',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    role VARCHAR(10) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN GENERATED ALWAYS AS (status = 'ACTIVE') STORED,
    signup_method VARCHAR(10) NOT NULL DEFAULT 'EMAIL',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    token_version INTEGER NOT NULL DEFAULT 0,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    lock_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    suspend_reason TEXT,
    suspended_at TIMESTAMPTZ,
    suspended_by UUID,
    pending_email VARCHAR(254),
    deletion_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_pending_email_lower CHECK (pending_email IS NULL OR pending_email = lower(pending_email)),
    CONSTRAINT ck_users_theme CHECK ((theme IS NULL OR theme IN ('light', 'dark'))),
    CONSTRAINT ck_users_language CHECK (language IN ('en', 'ko', 'km')),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_status CHECK (status IN ('UNVERIFIED', 'ACTIVE', 'SUSPENDED', 'PENDING_DELETION')),
    CONSTRAINT ck_users_signup_method CHECK (signup_method IN ('EMAIL', 'GOOGLE', 'GITHUB')),
    CONSTRAINT ck_users_counters CHECK (failed_attempts >= 0 AND lock_count >= 0 AND token_version >= 0),
    CONSTRAINT ck_users_suspend_reason CHECK (status <> 'SUSPENDED' OR (suspend_reason IS NOT NULL AND btrim(suspend_reason) <> '')),
    CONSTRAINT ck_users_deletion_requested CHECK (status <> 'PENDING_DELETION' OR deletion_requested_at IS NOT NULL),
    CONSTRAINT fk_users_suspended_by FOREIGN KEY (suspended_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_users_role ON users (role);
CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_users_last_login_at ON users (last_login_at);
CREATE INDEX ix_users_created_at ON users (created_at);
CREATE INDEX ix_users_suspended_by ON users (suspended_by) WHERE suspended_by IS NOT NULL;
CREATE UNIQUE INDEX uq_users_pending_email ON users (pending_email) WHERE pending_email IS NOT NULL;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE users IS 'Accounts, credentials state and lifecycle status.';

CREATE TABLE organizations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT ck_organizations_name CHECK (btrim(name) <> '')
);
CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON organizations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE organizations IS 'Workspaces that own chatbots.';

CREATE TABLE organization_members (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_organization_members PRIMARY KEY (id),
    CONSTRAINT fk_organization_members_organization_id FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_members_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_organization_members_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_organization_members_role CHECK (role IN ('ADMIN', 'MEMBER'))
);
CREATE INDEX ix_organization_members_user_id ON organization_members (user_id);
CREATE TRIGGER trg_organization_members_updated_at BEFORE UPDATE ON organization_members FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE organization_members IS 'Membership of users in organizations.';

CREATE TABLE password_resets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_resets PRIMARY KEY (id),
    CONSTRAINT fk_password_resets_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_password_resets_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_resets_user_id ON password_resets (user_id);
CREATE INDEX ix_password_resets_expires_at ON password_resets (expires_at);
CREATE TRIGGER trg_password_resets_updated_at BEFORE UPDATE ON password_resets FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE password_resets IS 'Hashed single-use password reset tokens.';

CREATE TABLE email_verifications (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    kind VARCHAR(15) NOT NULL,
    email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_email_verifications PRIMARY KEY (id),
    CONSTRAINT fk_email_verifications_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_email_verifications_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verifications_kind CHECK (kind IN ('SIGNUP', 'CHANGE_EMAIL'))
);
CREATE INDEX ix_email_verifications_user_id ON email_verifications (user_id);
CREATE TRIGGER trg_email_verifications_updated_at BEFORE UPDATE ON email_verifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE email_verifications IS 'Sign-up and change-email verification tokens.';

CREATE TABLE quota_defaults (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    singleton BOOLEAN NOT NULL DEFAULT TRUE,
    max_bots INTEGER NOT NULL DEFAULT 5,
    max_storage_bytes BIGINT NOT NULL DEFAULT 5368709120,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_quota_defaults PRIMARY KEY (id),
    CONSTRAINT uq_quota_defaults_singleton UNIQUE (singleton),
    CONSTRAINT ck_quota_defaults_singleton CHECK (singleton),
    CONSTRAINT ck_quota_defaults_bots CHECK (max_bots BETWEEN 1 AND 5),
    CONSTRAINT ck_quota_defaults_storage CHECK (max_storage_bytes BETWEEN 1 AND 5368709120),
    CONSTRAINT fk_quota_defaults_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE TRIGGER trg_quota_defaults_updated_at BEFORE UPDATE ON quota_defaults FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE quota_defaults IS 'Single-row default quota applied to new users.';

INSERT INTO quota_defaults (singleton, max_bots, max_storage_bytes) VALUES (TRUE, 5, 5368709120);

CREATE TABLE user_quotas (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    max_bots INTEGER NOT NULL,
    max_storage_bytes BIGINT NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_quotas PRIMARY KEY (id),
    CONSTRAINT fk_user_quotas_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_quotas_user_id UNIQUE (user_id),
    CONSTRAINT ck_user_quotas_bots CHECK (max_bots BETWEEN 1 AND 5),
    CONSTRAINT ck_user_quotas_storage CHECK (max_storage_bytes BETWEEN 1 AND 5368709120),
    CONSTRAINT fk_user_quotas_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE TRIGGER trg_user_quotas_updated_at BEFORE UPDATE ON user_quotas FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_quotas IS 'Per-user quota override (usage is computed from chatbots and documents).';

CREATE TABLE user_sessions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    device VARCHAR(160),
    user_agent TEXT,
    ip_address INET,
    location VARCHAR(160),
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(25),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT ck_user_sessions_revoked_reason CHECK ((revoked_reason IS NULL OR revoked_reason IN ('LOGOUT', 'USER_REVOKED', 'ADMIN_FORCE_LOGOUT', 'PASSWORD_RESET', 'ACCOUNT_SUSPENDED', 'MFA_RESET', 'EXPIRED'))),
    CONSTRAINT ck_user_sessions_revoked CHECK (revoked_at IS NULL OR revoked_reason IS NOT NULL)
);
CREATE INDEX ix_user_sessions_user_active ON user_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_sessions_expires_at ON user_sessions (expires_at);
CREATE TRIGGER trg_user_sessions_updated_at BEFORE UPDATE ON user_sessions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_sessions IS 'Signed-in devices; revoking a row signs that device out.';

CREATE TABLE user_totp (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    secret_encrypted TEXT NOT NULL,
    confirmed_at TIMESTAMPTZ,
    last_used_step BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_totp PRIMARY KEY (id),
    CONSTRAINT fk_user_totp_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_totp_user_id UNIQUE (user_id)
);
CREATE TRIGGER trg_user_totp_updated_at BEFORE UPDATE ON user_totp FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_totp IS 'Authenticator-app (TOTP) enrolment, one per user.';

CREATE TABLE user_passkeys (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    credential_id TEXT NOT NULL,
    public_key BYTEA NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(80) NOT NULL,
    transports TEXT[],
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_passkeys PRIMARY KEY (id),
    CONSTRAINT fk_user_passkeys_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_passkeys_credential_id UNIQUE (credential_id),
    CONSTRAINT ck_user_passkeys_sign_count CHECK (sign_count >= 0)
);
CREATE INDEX ix_user_passkeys_user_id ON user_passkeys (user_id);
CREATE TRIGGER trg_user_passkeys_updated_at BEFORE UPDATE ON user_passkeys FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_passkeys IS 'WebAuthn passkeys registered by a user.';

CREATE TABLE linked_accounts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(10) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_linked_accounts PRIMARY KEY (id),
    CONSTRAINT fk_linked_accounts_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_linked_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_linked_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_linked_accounts_provider CHECK (provider IN ('GOOGLE', 'GITHUB'))
);
CREATE TRIGGER trg_linked_accounts_updated_at BEFORE UPDATE ON linked_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE linked_accounts IS 'Google/GitHub identities linked to a user.';

CREATE TABLE notifications (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    channel VARCHAR(5) NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    delivery_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('EMAIL', 'PUSH')),
    CONSTRAINT ck_notifications_type CHECK (type IN ('KB_PROCESSING_FAILED', 'CRAWL_FAILED', 'LLM_FAILED', 'NEAR_LIMIT', 'REPORT_INACCURATE', 'REPORT_UNHELPFUL', 'CHATBOT_DISABLED', 'APPEAL_DECIDED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED', 'PASSWORD_RESET', 'EMAIL_VERIFICATION', 'ADMIN_HIGH_ERROR_RATE', 'ADMIN_UNHANDLED_ERROR', 'ADMIN_USER_REPORT', 'ADMIN_LLM_UNAVAILABLE', 'ADMIN_DB_FAILURE', 'ADMIN_API_ERROR', 'ADMIN_NEW_APPEAL')),
    CONSTRAINT ck_notifications_delivery_status CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX ix_notifications_user_inbox ON notifications (user_id, read_at, created_at DESC);
CREATE TRIGGER trg_notifications_updated_at BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE notifications IS 'E-mail and push messages for a user.';

CREATE TABLE push_devices (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    platform VARCHAR(7) NOT NULL,
    push_token TEXT NOT NULL,
    device_name VARCHAR(120),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_push_devices PRIMARY KEY (id),
    CONSTRAINT fk_push_devices_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_push_devices_push_token UNIQUE (push_token),
    CONSTRAINT ck_push_devices_platform CHECK (platform IN ('IOS', 'ANDROID'))
);
CREATE INDEX ix_push_devices_user_id ON push_devices (user_id);
CREATE TRIGGER trg_push_devices_updated_at BEFORE UPDATE ON push_devices FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE push_devices IS 'Mobile companion app push tokens.';

CREATE FUNCTION audit_logs_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    n jsonb;
    o jsonb;
    col text;
BEGIN
    -- The only change tolerated is a foreign key being nulled by ON DELETE SET NULL (listed in TG_ARGV).
    IF TG_OP = 'UPDATE' THEN
        n := to_jsonb(NEW) - 'updated_at';
        o := to_jsonb(OLD) - 'updated_at';
        FOREACH col IN ARRAY TG_ARGV LOOP
            IF n -> col = 'null'::jsonb AND o -> col <> 'null'::jsonb THEN
                n := n - col;
                o := o - col;
            END IF;
        END LOOP;
        IF n = o THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END
$$;

CREATE FUNCTION audit_logs_no_truncate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: TRUNCATE is not allowed' USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TABLE audit_logs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    admin_id UUID,
    admin_email VARCHAR(254) NOT NULL,
    action VARCHAR(30) NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id UUID NOT NULL,
    target_label VARCHAR(254) NOT NULL,
    reason TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_service VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_admin_id FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('FORCE_DISABLE_CHATBOT', 'REENABLE_CHATBOT', 'SUSPEND_USER', 'REACTIVATE_USER', 'APPROVE_APPEAL', 'REJECT_APPEAL', 'SET_QUOTA', 'RESET_MFA', 'FORCE_LOGOUT')),
    CONSTRAINT ck_audit_logs_target_type CHECK (target_type IN ('USER', 'CHATBOT', 'APPEAL')),
    CONSTRAINT ck_audit_logs_reason CHECK (btrim(reason) <> '')
);
CREATE INDEX ix_audit_logs_admin_email ON audit_logs (admin_email);
CREATE INDEX ix_audit_logs_action ON audit_logs (action);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id);
CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_admin_id ON audit_logs (admin_id) WHERE admin_id IS NOT NULL;
COMMENT ON TABLE audit_logs IS 'Append-only log of every admin action (UPDATE, DELETE and TRUNCATE are blocked by triggers).';

CREATE TRIGGER trg_audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable('admin_id');
CREATE TRIGGER trg_audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_no_truncate();


-- ====================================================================================================
-- SCHEMA catalog: models, prompt templates, admin limits, LLM status (catalog-service, 8082)
-- ====================================================================================================
CREATE SCHEMA catalog;
SET LOCAL search_path TO catalog, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE models (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'OLLAMA',
    model_identifier VARCHAR(200) NOT NULL,
    kind VARCHAR(10) NOT NULL DEFAULT 'LLM',
    embedding_dimensions INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_system_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_models PRIMARY KEY (id),
    CONSTRAINT uq_models_model_identifier UNIQUE (model_identifier),
    CONSTRAINT ck_models_provider CHECK (provider = 'OLLAMA'),
    CONSTRAINT ck_models_kind CHECK (kind IN ('LLM', 'EMBEDDING')),
    CONSTRAINT ck_models_embedding_dimensions CHECK ((kind = 'EMBEDDING' AND embedding_dimensions IS NOT NULL AND embedding_dimensions > 0) OR (kind = 'LLM' AND embedding_dimensions IS NULL)),
    CONSTRAINT ck_models_default_enabled CHECK (NOT is_default OR enabled),
    CONSTRAINT ck_models_fallback CHECK (NOT is_system_fallback OR (enabled AND kind = 'LLM'))
);
CREATE INDEX ix_models_kind_enabled ON models (kind, enabled);
CREATE UNIQUE INDEX uq_models_default_per_kind ON models (kind) WHERE is_default;
CREATE UNIQUE INDEX uq_models_system_fallback ON models ((TRUE)) WHERE is_system_fallback;
CREATE TRIGGER trg_models_updated_at BEFORE UPDATE ON models FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE models IS 'Platform LLM and embedding models (only enabled ones are offered to users).';

CREATE TABLE system_prompt_templates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    prompt TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_system_prompt_templates PRIMARY KEY (id),
    CONSTRAINT ck_system_prompt_templates_name CHECK (btrim(name) <> '')
);
CREATE TRIGGER trg_system_prompt_templates_updated_at BEFORE UPDATE ON system_prompt_templates FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE system_prompt_templates IS 'Reusable system prompt templates.';

CREATE TABLE advanced_setting_limits (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    setting_key VARCHAR(30) NOT NULL,
    min_value NUMERIC(12,3) NOT NULL,
    max_value NUMERIC(12,3) NOT NULL,
    default_value NUMERIC(12,3) NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_advanced_setting_limits PRIMARY KEY (id),
    CONSTRAINT uq_advanced_setting_limits_key UNIQUE (setting_key),
    CONSTRAINT ck_advanced_setting_limits_key CHECK (setting_key IN ('temperature', 'max_tokens', 'top_k', 'max_context_tokens', 'chunk_size', 'chunk_overlap')),
    CONSTRAINT ck_advanced_setting_limits_range CHECK (min_value <= default_value AND default_value <= max_value)
);
CREATE TRIGGER trg_advanced_setting_limits_updated_at BEFORE UPDATE ON advanced_setting_limits FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE advanced_setting_limits IS 'Admin-defined min, max and default for each advanced chatbot setting. -- ref: identity.users(id) in updated_by';

CREATE TABLE llm_providers (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'OPERATIONAL',
    status_message TEXT,
    checked_at TIMESTAMPTZ,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_llm_providers PRIMARY KEY (id),
    CONSTRAINT uq_llm_providers_provider UNIQUE (provider),
    CONSTRAINT ck_llm_providers_status CHECK (status IN ('OPERATIONAL', 'DEGRADED', 'DOWN'))
);
CREATE TRIGGER trg_llm_providers_updated_at BEFORE UPDATE ON llm_providers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE llm_providers IS 'Current health of each LLM provider.';

CREATE TABLE llm_status_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL,
    from_status VARCHAR(12),
    to_status VARCHAR(12) NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_llm_status_events PRIMARY KEY (id),
    CONSTRAINT fk_llm_status_events_provider FOREIGN KEY (provider) REFERENCES llm_providers (provider) ON DELETE CASCADE,
    CONSTRAINT ck_llm_status_events_to CHECK (to_status IN ('OPERATIONAL', 'DEGRADED', 'DOWN')),
    CONSTRAINT ck_llm_status_events_from CHECK ((from_status IS NULL OR from_status IN ('OPERATIONAL', 'DEGRADED', 'DOWN')))
);
CREATE INDEX ix_llm_status_events_provider ON llm_status_events (provider, created_at DESC);
CREATE TRIGGER trg_llm_status_events_updated_at BEFORE UPDATE ON llm_status_events FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE llm_status_events IS 'History of provider status changes (error alerts).';

-- Seed data. Identifiers must match `ollama list` on the Ollama host.
INSERT INTO models (name, provider, model_identifier, kind, embedding_dimensions, enabled, is_default, is_system_fallback) VALUES
    ('Llama 3.2 3B',     'OLLAMA', 'llama3.2:3b',      'LLM',       NULL, TRUE, TRUE,  FALSE),
    ('Qwen',             'OLLAMA', 'qwen2.5:3b',       'LLM',       NULL, TRUE, FALSE, TRUE),
    ('nomic-embed-text', 'OLLAMA', 'nomic-embed-text', 'EMBEDDING', 768,  TRUE, TRUE,  FALSE),
    ('bge-m3',           'OLLAMA', 'bge-m3',           'EMBEDDING', 1024, TRUE, FALSE, FALSE);
INSERT INTO system_prompt_templates (name, prompt, enabled) VALUES ('Helpful assistant', 'Answer using the provided knowledge.', TRUE);
INSERT INTO advanced_setting_limits (setting_key, min_value, max_value, default_value) VALUES
    ('temperature', 0, 1, 0.4), ('max_tokens', 128, 4096, 1024), ('top_k', 1, 20, 5),
    ('max_context_tokens', 512, 16384, 4096), ('chunk_size', 100, 2000, 500), ('chunk_overlap', 0, 500, 50);
INSERT INTO llm_providers (provider, status) VALUES ('OLLAMA', 'OPERATIONAL');


-- ====================================================================================================
-- SCHEMA chatbot: chatbots, settings, starter questions, appeals, status history (chatbot-service, 8083)
-- ====================================================================================================
CREATE SCHEMA chatbot;
SET LOCAL search_path TO chatbot, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE chatbots (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID,
    owner_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200) NOT NULL DEFAULT '',
    avatar TEXT,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    disabled_reason TEXT,
    disabled_at TIMESTAMPTZ,
    disabled_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbots PRIMARY KEY (id),
    CONSTRAINT ck_chatbots_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_chatbots_status CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'PAUSED', 'DISABLED')),
    CONSTRAINT ck_chatbots_disabled CHECK (status <> 'DISABLED' OR (disabled_reason IS NOT NULL AND btrim(disabled_reason) <> '' AND disabled_at IS NOT NULL))
);
CREATE INDEX ix_chatbots_owner_id ON chatbots (owner_id);
CREATE INDEX ix_chatbots_status ON chatbots (status);
CREATE INDEX ix_chatbots_owner_status ON chatbots (owner_id, status);
CREATE INDEX ix_chatbots_created_at ON chatbots (created_at);
CREATE INDEX ix_chatbots_organization_id ON chatbots (organization_id) WHERE organization_id IS NOT NULL;
CREATE TRIGGER trg_chatbots_updated_at BEFORE UPDATE ON chatbots FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbots IS 'Chatbots. -- ref: identity.users(id) in owner_id/disabled_by, identity.organizations(id) in organization_id';

CREATE TABLE chatbot_starter_questions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    position SMALLINT NOT NULL,
    question VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_starter_questions PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_starter_questions_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT uq_chatbot_starter_questions_position UNIQUE (chatbot_id, position),
    CONSTRAINT ck_chatbot_starter_questions_position CHECK (position BETWEEN 1 AND 4),
    CONSTRAINT ck_chatbot_starter_questions_question CHECK (btrim(question) <> '')
);
CREATE TRIGGER trg_chatbot_starter_questions_updated_at BEFORE UPDATE ON chatbot_starter_questions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbot_starter_questions IS 'Ordered starter questions, at most 4 per chatbot.';

CREATE TABLE chatbot_settings (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    model_id UUID,
    model_name VARCHAR(200) NOT NULL,
    fallback_model_id UUID,
    fallback_model_name VARCHAR(200),
    embedding_model VARCHAR(200) NOT NULL DEFAULT 'nomic-embed-text',
    prompt_template_id UUID,
    system_instruction TEXT NOT NULL DEFAULT 'Answer using the provided knowledge.',
    custom_instruction TEXT,
    tone VARCHAR(20) NOT NULL DEFAULT 'PROFESSIONAL',
    answer_length VARCHAR(10) NOT NULL DEFAULT 'CONCISE',
    formatting VARCHAR(5) NOT NULL DEFAULT 'RICH',
    language VARCHAR(4) NOT NULL DEFAULT 'AUTO',
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.4,
    max_tokens INTEGER NOT NULL DEFAULT 1024,
    top_k INTEGER NOT NULL DEFAULT 5,
    search_mode VARCHAR(10) NOT NULL DEFAULT 'HYBRID',
    max_context_tokens INTEGER NOT NULL DEFAULT 4096,
    chunk_size INTEGER NOT NULL DEFAULT 500,
    chunk_overlap INTEGER NOT NULL DEFAULT 50,
    answer_from_documents_only BOOLEAN NOT NULL DEFAULT TRUE,
    show_citations BOOLEAN NOT NULL DEFAULT TRUE,
    welcome_message VARCHAR(2000),
    fallback_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_settings PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_settings_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT uq_chatbot_settings_chatbot_id UNIQUE (chatbot_id),
    CONSTRAINT ck_chatbot_settings_tone CHECK (tone IN ('PROFESSIONAL', 'FRIENDLY', 'CASUAL', 'FORMAL', 'CUSTOM')),
    CONSTRAINT ck_chatbot_settings_answer_length CHECK (answer_length IN ('CONCISE', 'DETAILED')),
    CONSTRAINT ck_chatbot_settings_formatting CHECK (formatting IN ('RICH', 'PLAIN')),
    CONSTRAINT ck_chatbot_settings_language CHECK (language IN ('AUTO', 'EN', 'KM', 'KO')),
    CONSTRAINT ck_chatbot_settings_search_mode CHECK (search_mode IN ('SEMANTIC', 'KEYWORD', 'HYBRID')),
    CONSTRAINT ck_chatbot_settings_temperature CHECK (temperature >= 0 AND temperature <= 1),
    CONSTRAINT ck_chatbot_settings_max_tokens CHECK (max_tokens > 0),
    CONSTRAINT ck_chatbot_settings_top_k CHECK (top_k BETWEEN 1 AND 20),
    CONSTRAINT ck_chatbot_settings_max_context CHECK (max_context_tokens > 0),
    CONSTRAINT ck_chatbot_settings_chunking CHECK (chunk_size > 0 AND chunk_overlap >= 0 AND chunk_overlap < chunk_size),
    CONSTRAINT ck_chatbot_settings_fallback_differs CHECK (fallback_model_name IS NULL OR fallback_model_name <> model_name),
    CONSTRAINT ck_chatbot_settings_custom_tone CHECK (tone <> 'CUSTOM' OR (custom_instruction IS NOT NULL AND btrim(custom_instruction) <> ''))
);
CREATE TRIGGER trg_chatbot_settings_updated_at BEFORE UPDATE ON chatbot_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbot_settings IS '1:1 chatbot configuration. Admin min/max limits (catalog.advanced_setting_limits) are enforced by the application. -- ref: catalog.models(id), catalog.system_prompt_templates(id)';

CREATE FUNCTION chatbot_settings_embedding_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.embedding_model IS DISTINCT FROM OLD.embedding_model THEN
        RAISE EXCEPTION 'The embedding model cannot be changed after the chatbot is created' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_chatbot_settings_embedding_immutable BEFORE UPDATE ON chatbot_settings FOR EACH ROW EXECUTE FUNCTION chatbot_settings_embedding_immutable();

CREATE TABLE chatbot_appeals (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    decision_reason TEXT,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_appeals PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_appeals_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT ck_chatbot_appeals_message CHECK (btrim(message) <> ''),
    CONSTRAINT ck_chatbot_appeals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_chatbot_appeals_decided CHECK (status = 'PENDING' OR decided_at IS NOT NULL),
    CONSTRAINT ck_chatbot_appeals_rejected CHECK (status <> 'REJECTED' OR (decision_reason IS NOT NULL AND btrim(decision_reason) <> ''))
);
CREATE UNIQUE INDEX uq_chatbot_appeals_one_pending ON chatbot_appeals (chatbot_id) WHERE status = 'PENDING';
CREATE INDEX ix_chatbot_appeals_status ON chatbot_appeals (status, created_at);
CREATE INDEX ix_chatbot_appeals_owner_id ON chatbot_appeals (owner_id);
CREATE INDEX ix_chatbot_appeals_chatbot_id ON chatbot_appeals (chatbot_id);
CREATE TRIGGER trg_chatbot_appeals_updated_at BEFORE UPDATE ON chatbot_appeals FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbot_appeals IS 'Owner appeals against a DISABLED chatbot. -- ref: identity.users(id) in owner_id/decided_by';

CREATE TABLE chatbot_status_history (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    from_status VARCHAR(10),
    to_status VARCHAR(10) NOT NULL,
    changed_by UUID,
    actor_role VARCHAR(6) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_status_history PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_status_history_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT ck_chatbot_status_history_from CHECK ((from_status IS NULL OR from_status IN ('DRAFT', 'PENDING', 'ACTIVE', 'PAUSED', 'DISABLED'))),
    CONSTRAINT ck_chatbot_status_history_to CHECK (to_status IN ('DRAFT', 'PENDING', 'ACTIVE', 'PAUSED', 'DISABLED')),
    CONSTRAINT ck_chatbot_status_history_actor CHECK (actor_role IN ('OWNER', 'ADMIN', 'SYSTEM'))
);
CREATE INDEX ix_chatbot_status_history_chatbot ON chatbot_status_history (chatbot_id, created_at DESC);
COMMENT ON TABLE chatbot_status_history IS 'Append-only history of chatbot status changes. -- ref: identity.users(id) in changed_by';


-- ====================================================================================================
-- SCHEMA knowledge: sources, documents, chunks, ingestion, crawl, vector cleanup (knowledge-service, 8084)
-- ====================================================================================================
CREATE SCHEMA knowledge;
SET LOCAL search_path TO knowledge, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE knowledge_sources (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    source_type VARCHAR(10) NOT NULL DEFAULT 'DOCUMENT',
    source_key VARCHAR(512) NOT NULL,
    url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_knowledge_sources PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_sources_key UNIQUE (chatbot_id, source_type, source_key),
    CONSTRAINT ck_knowledge_sources_type CHECK (source_type IN ('DOCUMENT', 'WEBSITE'))
);
CREATE INDEX ix_knowledge_sources_chatbot_id ON knowledge_sources (chatbot_id);
CREATE INDEX ix_knowledge_sources_owner_id ON knowledge_sources (owner_id);
CREATE TRIGGER trg_knowledge_sources_updated_at BEFORE UPDATE ON knowledge_sources FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE knowledge_sources IS 'A document or website a chatbot learns from. -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE documents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255),
    content_hash CHAR(64),
    status VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
    error_message VARCHAR(2000),
    chunk_count INTEGER NOT NULL DEFAULT 0,
    embedding_model VARCHAR(200),
    chunk_size INTEGER,
    chunk_overlap INTEGER,
    retry_count INTEGER NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_source_id FOREIGN KEY (source_id) REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    CONSTRAINT uq_documents_source_id UNIQUE (source_id),
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_documents_file_type CHECK (file_type IN ('PDF', 'DOCX', 'TXT', 'MD', 'XLSX', 'WEBSITE')),
    CONSTRAINT ck_documents_status CHECK (status IN ('QUEUED', 'UPLOADING', 'PROCESSING', 'CRAWLING', 'EXTRACTING', 'CHUNKING', 'INDEXING', 'READY', 'FAILED', 'REVIEW')),
    CONSTRAINT ck_documents_size CHECK (size_bytes > 0 AND (file_type = 'WEBSITE' OR size_bytes <= 20971520)),
    CONSTRAINT ck_documents_chunk_count CHECK (chunk_count >= 0 AND retry_count >= 0),
    CONSTRAINT ck_documents_failed_message CHECK (status <> 'FAILED' OR error_message IS NOT NULL)
);
CREATE INDEX ix_documents_chatbot_id ON documents (chatbot_id);
CREATE INDEX ix_documents_owner_id ON documents (owner_id);
CREATE INDEX ix_documents_status ON documents (status);
CREATE INDEX ix_documents_chatbot_status ON documents (chatbot_id, status);
CREATE INDEX ix_documents_chatbot_type ON documents (chatbot_id, file_type);
CREATE INDEX ix_documents_content_hash ON documents (content_hash) WHERE content_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_documents_chatbot_name ON documents (chatbot_id, lower(name));
CREATE TRIGGER trg_documents_updated_at BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE documents IS 'Indexed documents (20 MB file limit). -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE document_chunks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    page INTEGER,
    sheet VARCHAR(120),
    row_number INTEGER,
    content TEXT NOT NULL,
    token_count INTEGER,
    vector_id UUID,
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_document_chunks PRIMARY KEY (id),
    CONSTRAINT fk_document_chunks_document_id FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT uq_document_chunks_index UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_document_chunks_index CHECK (chunk_index >= 0)
);
CREATE INDEX ix_document_chunks_tsv ON document_chunks USING GIN (content_tsv);
CREATE INDEX ix_document_chunks_vector_id ON document_chunks (vector_id) WHERE vector_id IS NOT NULL;
CREATE TRIGGER trg_document_chunks_updated_at BEFORE UPDATE ON document_chunks FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE document_chunks IS 'Text chunks used for retrieval; content_tsv powers keyword search.';

CREATE TABLE ingestion_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    status VARCHAR(12) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingestion_jobs PRIMARY KEY (id),
    CONSTRAINT fk_ingestion_jobs_document_id FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT ck_ingestion_jobs_status CHECK (status IN ('QUEUED', 'UPLOADING', 'PROCESSING', 'CRAWLING', 'EXTRACTING', 'CHUNKING', 'INDEXING', 'READY', 'FAILED', 'REVIEW')),
    CONSTRAINT ck_ingestion_jobs_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_ingestion_jobs_document_id ON ingestion_jobs (document_id);
CREATE TRIGGER trg_ingestion_jobs_updated_at BEFORE UPDATE ON ingestion_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE ingestion_jobs IS 'Processing attempts for a document.';

CREATE TABLE crawl_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    start_url VARCHAR(2048) NOT NULL,
    max_depth SMALLINT NOT NULL,
    max_pages SMALLINT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PREVIEW',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_crawl_jobs PRIMARY KEY (id),
    CONSTRAINT ck_crawl_jobs_depth CHECK (max_depth BETWEEN 1 AND 3),
    CONSTRAINT ck_crawl_jobs_pages CHECK (max_pages BETWEEN 50 AND 100),
    CONSTRAINT ck_crawl_jobs_status CHECK (status IN ('PREVIEW', 'IMPORTING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX ix_crawl_jobs_chatbot_id ON crawl_jobs (chatbot_id);
CREATE INDEX ix_crawl_jobs_owner_id ON crawl_jobs (owner_id);
CREATE TRIGGER trg_crawl_jobs_updated_at BEFORE UPDATE ON crawl_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE crawl_jobs IS 'Site crawl: preview first, then import. -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE crawl_pages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    crawl_job_id UUID NOT NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500),
    content_preview TEXT,
    depth SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(8) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    document_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_crawl_pages PRIMARY KEY (id),
    CONSTRAINT fk_crawl_pages_crawl_job_id FOREIGN KEY (crawl_job_id) REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_crawl_pages_document_id FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE SET NULL,
    CONSTRAINT uq_crawl_pages_job_url UNIQUE (crawl_job_id, url),
    CONSTRAINT ck_crawl_pages_status CHECK (status IN ('OK', 'EMPTY', 'BLOCKED', 'FAILED')),
    CONSTRAINT ck_crawl_pages_depth CHECK (depth >= 0)
);
CREATE INDEX ix_crawl_pages_document_id ON crawl_pages (document_id) WHERE document_id IS NOT NULL;
CREATE TRIGGER trg_crawl_pages_updated_at BEFORE UPDATE ON crawl_pages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE crawl_pages IS 'Pages found by a crawl, previewed before import.';

CREATE TABLE vector_cleanup_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    document_id UUID,
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_vector_cleanup_jobs PRIMARY KEY (id),
    CONSTRAINT ck_vector_cleanup_jobs_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_vector_cleanup_jobs_next_attempt_at ON vector_cleanup_jobs (next_attempt_at);
CREATE TRIGGER trg_vector_cleanup_jobs_updated_at BEFORE UPDATE ON vector_cleanup_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE vector_cleanup_jobs IS 'Deferred Qdrant cleanup. Deliberately NO foreign keys: the task must outlive the deleted rows.';

CREATE VIEW v_owner_storage_usage AS
    SELECT owner_id, SUM(size_bytes)::bigint AS bytes_used, COUNT(*)::bigint AS documents
    FROM documents
    GROUP BY owner_id;
COMMENT ON VIEW v_owner_storage_usage IS 'Storage used by each owner, for quota checks.';


-- ====================================================================================================
-- SCHEMA conversation: playground chats, messages, citations, guest feedback and reports (conversation-service, 8085)
-- ====================================================================================================
CREATE SCHEMA conversation;
SET LOCAL search_path TO conversation, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE conversations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL DEFAULT 'PLAYGROUND',
    integration_id UUID,
    session_hash VARCHAR(64),
    title VARCHAR(200) NOT NULL DEFAULT 'New conversation',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT ck_conversations_playground_only CHECK (channel = 'PLAYGROUND')
);
CREATE INDEX ix_conversations_chatbot_updated ON conversations (chatbot_id, updated_at DESC);
CREATE INDEX ix_conversations_owner_id ON conversations (owner_id);
CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON conversations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE conversations IS 'Playground chats only. V5 never stores guest conversations (integration_id and session_hash are kept for compatibility). -- ref: chatbot.chatbots(id), identity.users(id), channel.channel_integrations(id)';

CREATE TABLE messages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    model_name VARCHAR(200),
    tokens_in INTEGER,
    tokens_out INTEGER,
    latency_ms INTEGER,
    unanswered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT uq_messages_sequence UNIQUE (conversation_id, sequence),
    CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_messages_tokens CHECK ((tokens_in IS NULL OR tokens_in >= 0) AND (tokens_out IS NULL OR tokens_out >= 0) AND (latency_ms IS NULL OR latency_ms >= 0))
);
CREATE TRIGGER trg_messages_updated_at BEFORE UPDATE ON messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE messages IS 'Messages of a playground conversation.';

CREATE TABLE message_sources (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    document_id UUID,
    chunk_id UUID,
    document_name VARCHAR(255) NOT NULL,
    excerpt TEXT NOT NULL,
    page INTEGER,
    sheet VARCHAR(120),
    row_number INTEGER,
    chunk_index INTEGER,
    score DOUBLE PRECISION NOT NULL,
    url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_message_sources PRIMARY KEY (id),
    CONSTRAINT fk_message_sources_message_id FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE
);
CREATE INDEX ix_message_sources_message_id ON message_sources (message_id);
CREATE TRIGGER trg_message_sources_updated_at BEFORE UPDATE ON message_sources FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE message_sources IS 'Citations. document_id and chunk_id are plain references (no FK) so history survives re-indexing. -- ref: knowledge.documents(id), knowledge.document_chunks(id)';

CREATE TABLE answer_feedback (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    integration_id UUID,
    answer_ref VARCHAR(64) NOT NULL,
    kind VARCHAR(6) NOT NULL,
    rating VARCHAR(11),
    reason VARCHAR(10),
    comment VARCHAR(500),
    question TEXT,
    answer TEXT,
    sources JSONB,
    status VARCHAR(9),
    handled_by UUID,
    handled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_answer_feedback PRIMARY KEY (id),
    CONSTRAINT uq_answer_feedback_answer_ref UNIQUE (answer_ref),
    CONSTRAINT ck_answer_feedback_channel CHECK (channel IN ('PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM')),
    CONSTRAINT ck_answer_feedback_kind CHECK (kind IN ('RATING', 'REPORT')),
    CONSTRAINT ck_answer_feedback_rating CHECK ((rating IS NULL OR rating IN ('HELPFUL', 'NOT_HELPFUL'))),
    CONSTRAINT ck_answer_feedback_reason CHECK ((reason IS NULL OR reason IN ('INACCURATE', 'UNHELPFUL'))),
    CONSTRAINT ck_answer_feedback_status CHECK ((status IS NULL OR status IN ('OPEN', 'RESOLVED', 'DISMISSED'))),
    CONSTRAINT ck_answer_feedback_rating_shape CHECK (kind <> 'RATING' OR (rating IS NOT NULL AND reason IS NULL AND comment IS NULL AND question IS NULL AND answer IS NULL AND sources IS NULL AND status IS NULL AND handled_by IS NULL AND handled_at IS NULL)),
    CONSTRAINT ck_answer_feedback_report_shape CHECK (kind <> 'REPORT' OR (reason IS NOT NULL AND question IS NOT NULL AND answer IS NOT NULL AND status IS NOT NULL AND rating IS NULL)),
    CONSTRAINT ck_answer_feedback_handled CHECK (status IS NULL OR status = 'OPEN' OR handled_at IS NOT NULL)
);
CREATE INDEX ix_answer_feedback_chatbot_status ON answer_feedback (chatbot_id, status, created_at DESC);
CREATE INDEX ix_answer_feedback_chatbot_reason ON answer_feedback (chatbot_id, reason);
CREATE INDEX ix_answer_feedback_kind ON answer_feedback (kind);
CREATE INDEX ix_answer_feedback_owner_id ON answer_feedback (owner_id);
CREATE TRIGGER trg_answer_feedback_updated_at BEFORE UPDATE ON answer_feedback FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE answer_feedback IS 'Guest answer ratings and reports. One feedback per answer; only reports keep the question and answer. -- ref: chatbot.chatbots(id), identity.users(id), channel.channel_integrations(id)';


-- ====================================================================================================
-- SCHEMA channel: public link, widget, Telegram, allowed domains, retired links (channel-service, 8086)
-- ====================================================================================================
CREATE SCHEMA channel;
SET LOCAL search_path TO channel, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE channel_integrations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    public_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(12) NOT NULL DEFAULT 'OFF',
    error_reason TEXT,
    settings TEXT,
    expires_at TIMESTAMPTZ,
    access_password_hash VARCHAR(255),
    regenerated_at TIMESTAMPTZ,
    credential TEXT,
    token_mask VARCHAR(40),
    webhook_secret VARCHAR(64),
    telegram_bot_id VARCHAR(40),
    telegram_username VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_channel_integrations PRIMARY KEY (id),
    CONSTRAINT uq_channel_integrations_public_id UNIQUE (public_id),
    CONSTRAINT uq_channel_integrations_chatbot_channel UNIQUE (chatbot_id, channel),
    CONSTRAINT ck_channel_integrations_channel CHECK (channel IN ('PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM')),
    CONSTRAINT ck_channel_integrations_status CHECK (status IN ('LIVE', 'OFF', 'CONNECTED', 'DISCONNECTED', 'ERROR')),
    CONSTRAINT ck_channel_integrations_error CHECK (status <> 'ERROR' OR error_reason IS NOT NULL)
);
CREATE INDEX ix_channel_integrations_chatbot_id ON channel_integrations (chatbot_id);
CREATE INDEX ix_channel_integrations_owner_id ON channel_integrations (owner_id);
CREATE INDEX ix_channel_integrations_status ON channel_integrations (status);
CREATE UNIQUE INDEX uq_channel_integrations_telegram_bot ON channel_integrations (telegram_bot_id) WHERE telegram_bot_id IS NOT NULL;
CREATE TRIGGER trg_channel_integrations_updated_at BEFORE UPDATE ON channel_integrations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE channel_integrations IS 'Public link, web widget and Telegram per chatbot. credential is ENCRYPTED, access_password_hash is HASHED. -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE channel_allowed_domains (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    domain VARCHAR(253) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_channel_allowed_domains PRIMARY KEY (id),
    CONSTRAINT fk_channel_allowed_domains_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE CASCADE,
    CONSTRAINT uq_channel_allowed_domains UNIQUE (integration_id, domain),
    CONSTRAINT ck_channel_allowed_domains_domain CHECK (domain = lower(domain) AND domain ~ '^([a-z0-9-]+\.)*[a-z0-9-]+(:[0-9]+)?$')
);
CREATE TRIGGER trg_channel_allowed_domains_updated_at BEFORE UPDATE ON channel_allowed_domains FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE channel_allowed_domains IS 'Websites allowed to embed a web widget.';

CREATE TABLE retired_public_ids (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    public_id VARCHAR(64) NOT NULL,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    reason VARCHAR(16) NOT NULL,
    retired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_retired_public_ids PRIMARY KEY (id),
    CONSTRAINT uq_retired_public_ids_public_id UNIQUE (public_id),
    CONSTRAINT ck_retired_public_ids_reason CHECK (reason IN ('CHATBOT_DELETED', 'REGENERATED', 'CHANNEL_REMOVED'))
);
CREATE INDEX ix_retired_public_ids_chatbot_id ON retired_public_ids (chatbot_id);
CREATE TRIGGER trg_retired_public_ids_updated_at BEFORE UPDATE ON retired_public_ids FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE retired_public_ids IS 'Old public tokens, so guests see the right message (CHATBOT_DELETED shows ''This chatbot is no longer available!''). -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE telegram_updates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    update_id BIGINT NOT NULL,
    answer TEXT,
    sent_parts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_telegram_updates PRIMARY KEY (id),
    CONSTRAINT fk_telegram_updates_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE CASCADE,
    CONSTRAINT uq_telegram_updates_update UNIQUE (integration_id, update_id),
    CONSTRAINT ck_telegram_updates_parts CHECK (sent_parts >= 0)
);
CREATE TRIGGER trg_telegram_updates_updated_at BEFORE UPDATE ON telegram_updates FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE telegram_updates IS 'Telegram webhook idempotency records.';

CREATE TABLE channel_messages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_channel_messages PRIMARY KEY (id),
    CONSTRAINT fk_channel_messages_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE CASCADE
);
CREATE INDEX ix_channel_messages_integration_time ON channel_messages (integration_id, created_at);
CREATE TRIGGER trg_channel_messages_updated_at BEFORE UPDATE ON channel_messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE channel_messages IS 'One row per guest exchange, for ''messages this week'' per channel.';


-- ====================================================================================================
-- SCHEMA analytics: message event log and reporting views (analytics-service, 8087)
-- ====================================================================================================
CREATE SCHEMA analytics;
SET LOCAL search_path TO analytics, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE analytics_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    messages INTEGER NOT NULL DEFAULT 2,
    occurred_at TIMESTAMPTZ NOT NULL,
    session_id VARCHAR(64),
    guest_id VARCHAR(64),
    question TEXT,
    question_hash CHAR(64),
    unanswered BOOLEAN NOT NULL DEFAULT FALSE,
    tokens_in INTEGER NOT NULL DEFAULT 0,
    tokens_out INTEGER NOT NULL DEFAULT 0,
    model_name VARCHAR(200),
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_analytics_events PRIMARY KEY (id),
    CONSTRAINT ck_analytics_events_channel CHECK (channel IN ('PLAYGROUND', 'PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM')),
    CONSTRAINT ck_analytics_events_counts CHECK (messages >= 0 AND tokens_in >= 0 AND tokens_out >= 0 AND (latency_ms IS NULL OR latency_ms >= 0))
);
CREATE INDEX ix_analytics_events_owner_time ON analytics_events (owner_id, occurred_at);
CREATE INDEX ix_analytics_events_chatbot_time ON analytics_events (chatbot_id, occurred_at);
CREATE INDEX ix_analytics_events_time ON analytics_events (occurred_at);
CREATE INDEX ix_analytics_events_chatbot_session ON analytics_events (chatbot_id, session_id) WHERE session_id IS NOT NULL;
CREATE INDEX ix_analytics_events_unanswered ON analytics_events (chatbot_id, question_hash) WHERE unanswered;
CREATE INDEX ix_analytics_events_question ON analytics_events (chatbot_id, question_hash) WHERE question_hash IS NOT NULL;
CREATE TRIGGER trg_analytics_events_updated_at BEFORE UPDATE ON analytics_events FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE analytics_events IS 'One row per question/answer exchange. question is kept for most-asked and unanswered reports (a privacy trade-off); guests are anonymous ids. -- ref: identity.users(id), chatbot.chatbots(id)';

CREATE VIEW v_chatbot_daily AS
    SELECT (occurred_at AT TIME ZONE 'UTC')::date AS day, chatbot_id, owner_id, channel,
           SUM(messages)::bigint AS messages, COUNT(DISTINCT session_id) AS sessions, COUNT(DISTINCT guest_id) AS guests,
           COUNT(*) FILTER (WHERE unanswered) AS unanswered_count, SUM(tokens_in + tokens_out)::bigint AS tokens
    FROM analytics_events
    GROUP BY 1, 2, 3, 4;
COMMENT ON VIEW v_chatbot_daily IS 'Daily messages, sessions, guests, unanswered questions and tokens per chatbot and channel. Active chat sessions = distinct session_id with an event in the last 30 minutes (query analytics_events).';

CREATE VIEW v_owner_token_usage AS
    SELECT owner_id, date_trunc('month', occurred_at AT TIME ZONE 'UTC')::date AS month, SUM(tokens_in + tokens_out)::bigint AS tokens
    FROM analytics_events
    GROUP BY 1, 2;
COMMENT ON VIEW v_owner_token_usage IS 'Monthly token usage per owner (top users by token usage).';

CREATE VIEW v_platform_daily AS
    SELECT (occurred_at AT TIME ZONE 'UTC')::date AS day, SUM(messages)::bigint AS messages, COUNT(DISTINCT chatbot_id) AS active_chatbots, SUM(tokens_in + tokens_out)::bigint AS tokens
    FROM analytics_events
    GROUP BY 1;
COMMENT ON VIEW v_platform_daily IS 'Platform-wide daily message volume.';


-- ====================================================================================================
-- SCHEMA fastapi: monolith used by the FastAPI RAG backend, same tables as above with real foreign keys
-- ====================================================================================================
CREATE SCHEMA fastapi;
SET LOCAL search_path TO fastapi, pg_catalog;

CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TABLE users (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL DEFAULT '',
    display_name VARCHAR(120),
    bio VARCHAR(2000),
    avatar TEXT,
    theme VARCHAR(10),
    language VARCHAR(5) NOT NULL DEFAULT 'en',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    role VARCHAR(10) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN GENERATED ALWAYS AS (status = 'ACTIVE') STORED,
    signup_method VARCHAR(10) NOT NULL DEFAULT 'EMAIL',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    token_version INTEGER NOT NULL DEFAULT 0,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    lock_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    suspend_reason TEXT,
    suspended_at TIMESTAMPTZ,
    suspended_by UUID,
    pending_email VARCHAR(254),
    deletion_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_pending_email_lower CHECK (pending_email IS NULL OR pending_email = lower(pending_email)),
    CONSTRAINT ck_users_theme CHECK ((theme IS NULL OR theme IN ('light', 'dark'))),
    CONSTRAINT ck_users_language CHECK (language IN ('en', 'ko', 'km')),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_status CHECK (status IN ('UNVERIFIED', 'ACTIVE', 'SUSPENDED', 'PENDING_DELETION')),
    CONSTRAINT ck_users_signup_method CHECK (signup_method IN ('EMAIL', 'GOOGLE', 'GITHUB')),
    CONSTRAINT ck_users_counters CHECK (failed_attempts >= 0 AND lock_count >= 0 AND token_version >= 0),
    CONSTRAINT ck_users_suspend_reason CHECK (status <> 'SUSPENDED' OR (suspend_reason IS NOT NULL AND btrim(suspend_reason) <> '')),
    CONSTRAINT ck_users_deletion_requested CHECK (status <> 'PENDING_DELETION' OR deletion_requested_at IS NOT NULL),
    CONSTRAINT fk_users_suspended_by FOREIGN KEY (suspended_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_users_role ON users (role);
CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_users_last_login_at ON users (last_login_at);
CREATE INDEX ix_users_created_at ON users (created_at);
CREATE INDEX ix_users_suspended_by ON users (suspended_by) WHERE suspended_by IS NOT NULL;
CREATE UNIQUE INDEX uq_users_pending_email ON users (pending_email) WHERE pending_email IS NOT NULL;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE users IS 'Accounts, credentials state and lifecycle status.';

CREATE TABLE organizations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT ck_organizations_name CHECK (btrim(name) <> '')
);
CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON organizations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE organizations IS 'Workspaces that own chatbots.';

CREATE TABLE organization_members (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_organization_members PRIMARY KEY (id),
    CONSTRAINT fk_organization_members_organization_id FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_members_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_organization_members_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_organization_members_role CHECK (role IN ('ADMIN', 'MEMBER'))
);
CREATE INDEX ix_organization_members_user_id ON organization_members (user_id);
CREATE TRIGGER trg_organization_members_updated_at BEFORE UPDATE ON organization_members FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE organization_members IS 'Membership of users in organizations.';

CREATE TABLE password_resets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_resets PRIMARY KEY (id),
    CONSTRAINT fk_password_resets_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_password_resets_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_resets_user_id ON password_resets (user_id);
CREATE INDEX ix_password_resets_expires_at ON password_resets (expires_at);
CREATE TRIGGER trg_password_resets_updated_at BEFORE UPDATE ON password_resets FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE password_resets IS 'Hashed single-use password reset tokens.';

CREATE TABLE email_verifications (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    kind VARCHAR(15) NOT NULL,
    email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_email_verifications PRIMARY KEY (id),
    CONSTRAINT fk_email_verifications_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_email_verifications_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verifications_kind CHECK (kind IN ('SIGNUP', 'CHANGE_EMAIL'))
);
CREATE INDEX ix_email_verifications_user_id ON email_verifications (user_id);
CREATE TRIGGER trg_email_verifications_updated_at BEFORE UPDATE ON email_verifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE email_verifications IS 'Sign-up and change-email verification tokens.';

CREATE TABLE quota_defaults (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    singleton BOOLEAN NOT NULL DEFAULT TRUE,
    max_bots INTEGER NOT NULL DEFAULT 5,
    max_storage_bytes BIGINT NOT NULL DEFAULT 5368709120,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_quota_defaults PRIMARY KEY (id),
    CONSTRAINT uq_quota_defaults_singleton UNIQUE (singleton),
    CONSTRAINT ck_quota_defaults_singleton CHECK (singleton),
    CONSTRAINT ck_quota_defaults_bots CHECK (max_bots BETWEEN 1 AND 5),
    CONSTRAINT ck_quota_defaults_storage CHECK (max_storage_bytes BETWEEN 1 AND 5368709120),
    CONSTRAINT fk_quota_defaults_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE TRIGGER trg_quota_defaults_updated_at BEFORE UPDATE ON quota_defaults FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE quota_defaults IS 'Single-row default quota applied to new users.';

INSERT INTO quota_defaults (singleton, max_bots, max_storage_bytes) VALUES (TRUE, 5, 5368709120);

CREATE TABLE user_quotas (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    max_bots INTEGER NOT NULL,
    max_storage_bytes BIGINT NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_quotas PRIMARY KEY (id),
    CONSTRAINT fk_user_quotas_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_quotas_user_id UNIQUE (user_id),
    CONSTRAINT ck_user_quotas_bots CHECK (max_bots BETWEEN 1 AND 5),
    CONSTRAINT ck_user_quotas_storage CHECK (max_storage_bytes BETWEEN 1 AND 5368709120),
    CONSTRAINT fk_user_quotas_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE TRIGGER trg_user_quotas_updated_at BEFORE UPDATE ON user_quotas FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_quotas IS 'Per-user quota override (usage is computed from chatbots and documents).';

CREATE TABLE user_sessions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    device VARCHAR(160),
    user_agent TEXT,
    ip_address INET,
    location VARCHAR(160),
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(25),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT ck_user_sessions_revoked_reason CHECK ((revoked_reason IS NULL OR revoked_reason IN ('LOGOUT', 'USER_REVOKED', 'ADMIN_FORCE_LOGOUT', 'PASSWORD_RESET', 'ACCOUNT_SUSPENDED', 'MFA_RESET', 'EXPIRED'))),
    CONSTRAINT ck_user_sessions_revoked CHECK (revoked_at IS NULL OR revoked_reason IS NOT NULL)
);
CREATE INDEX ix_user_sessions_user_active ON user_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_sessions_expires_at ON user_sessions (expires_at);
CREATE TRIGGER trg_user_sessions_updated_at BEFORE UPDATE ON user_sessions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_sessions IS 'Signed-in devices; revoking a row signs that device out.';

CREATE TABLE user_totp (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    secret_encrypted TEXT NOT NULL,
    confirmed_at TIMESTAMPTZ,
    last_used_step BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_totp PRIMARY KEY (id),
    CONSTRAINT fk_user_totp_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_totp_user_id UNIQUE (user_id)
);
CREATE TRIGGER trg_user_totp_updated_at BEFORE UPDATE ON user_totp FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_totp IS 'Authenticator-app (TOTP) enrolment, one per user.';

CREATE TABLE user_passkeys (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    credential_id TEXT NOT NULL,
    public_key BYTEA NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(80) NOT NULL,
    transports TEXT[],
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_passkeys PRIMARY KEY (id),
    CONSTRAINT fk_user_passkeys_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_passkeys_credential_id UNIQUE (credential_id),
    CONSTRAINT ck_user_passkeys_sign_count CHECK (sign_count >= 0)
);
CREATE INDEX ix_user_passkeys_user_id ON user_passkeys (user_id);
CREATE TRIGGER trg_user_passkeys_updated_at BEFORE UPDATE ON user_passkeys FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE user_passkeys IS 'WebAuthn passkeys registered by a user.';

CREATE TABLE linked_accounts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(10) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_linked_accounts PRIMARY KEY (id),
    CONSTRAINT fk_linked_accounts_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_linked_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_linked_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_linked_accounts_provider CHECK (provider IN ('GOOGLE', 'GITHUB'))
);
CREATE TRIGGER trg_linked_accounts_updated_at BEFORE UPDATE ON linked_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE linked_accounts IS 'Google/GitHub identities linked to a user.';

CREATE TABLE notifications (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    channel VARCHAR(5) NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    delivery_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('EMAIL', 'PUSH')),
    CONSTRAINT ck_notifications_type CHECK (type IN ('KB_PROCESSING_FAILED', 'CRAWL_FAILED', 'LLM_FAILED', 'NEAR_LIMIT', 'REPORT_INACCURATE', 'REPORT_UNHELPFUL', 'CHATBOT_DISABLED', 'APPEAL_DECIDED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED', 'PASSWORD_RESET', 'EMAIL_VERIFICATION', 'ADMIN_HIGH_ERROR_RATE', 'ADMIN_UNHANDLED_ERROR', 'ADMIN_USER_REPORT', 'ADMIN_LLM_UNAVAILABLE', 'ADMIN_DB_FAILURE', 'ADMIN_API_ERROR', 'ADMIN_NEW_APPEAL')),
    CONSTRAINT ck_notifications_delivery_status CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX ix_notifications_user_inbox ON notifications (user_id, read_at, created_at DESC);
CREATE TRIGGER trg_notifications_updated_at BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE notifications IS 'E-mail and push messages for a user.';

CREATE TABLE push_devices (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    platform VARCHAR(7) NOT NULL,
    push_token TEXT NOT NULL,
    device_name VARCHAR(120),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_push_devices PRIMARY KEY (id),
    CONSTRAINT fk_push_devices_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_push_devices_push_token UNIQUE (push_token),
    CONSTRAINT ck_push_devices_platform CHECK (platform IN ('IOS', 'ANDROID'))
);
CREATE INDEX ix_push_devices_user_id ON push_devices (user_id);
CREATE TRIGGER trg_push_devices_updated_at BEFORE UPDATE ON push_devices FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE push_devices IS 'Mobile companion app push tokens.';

CREATE FUNCTION audit_logs_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    n jsonb;
    o jsonb;
    col text;
BEGIN
    -- The only change tolerated is a foreign key being nulled by ON DELETE SET NULL (listed in TG_ARGV).
    IF TG_OP = 'UPDATE' THEN
        n := to_jsonb(NEW) - 'updated_at';
        o := to_jsonb(OLD) - 'updated_at';
        FOREACH col IN ARRAY TG_ARGV LOOP
            IF n -> col = 'null'::jsonb AND o -> col <> 'null'::jsonb THEN
                n := n - col;
                o := o - col;
            END IF;
        END LOOP;
        IF n = o THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END
$$;

CREATE FUNCTION audit_logs_no_truncate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: TRUNCATE is not allowed' USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TABLE audit_logs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    admin_id UUID,
    admin_email VARCHAR(254) NOT NULL,
    action VARCHAR(30) NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id UUID NOT NULL,
    target_label VARCHAR(254) NOT NULL,
    reason TEXT NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_service VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_admin_id FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('FORCE_DISABLE_CHATBOT', 'REENABLE_CHATBOT', 'SUSPEND_USER', 'REACTIVATE_USER', 'APPROVE_APPEAL', 'REJECT_APPEAL', 'SET_QUOTA', 'RESET_MFA', 'FORCE_LOGOUT')),
    CONSTRAINT ck_audit_logs_target_type CHECK (target_type IN ('USER', 'CHATBOT', 'APPEAL')),
    CONSTRAINT ck_audit_logs_reason CHECK (btrim(reason) <> '')
);
CREATE INDEX ix_audit_logs_admin_email ON audit_logs (admin_email);
CREATE INDEX ix_audit_logs_action ON audit_logs (action);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id);
CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_logs_admin_id ON audit_logs (admin_id) WHERE admin_id IS NOT NULL;
COMMENT ON TABLE audit_logs IS 'Append-only log of every admin action (UPDATE, DELETE and TRUNCATE are blocked by triggers).';

CREATE TRIGGER trg_audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable('admin_id', 'organization_id');
CREATE TRIGGER trg_audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_no_truncate();

CREATE TABLE models (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'OLLAMA',
    model_identifier VARCHAR(200) NOT NULL,
    kind VARCHAR(10) NOT NULL DEFAULT 'LLM',
    embedding_dimensions INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_system_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_models PRIMARY KEY (id),
    CONSTRAINT uq_models_model_identifier UNIQUE (model_identifier),
    CONSTRAINT ck_models_provider CHECK (provider = 'OLLAMA'),
    CONSTRAINT ck_models_kind CHECK (kind IN ('LLM', 'EMBEDDING')),
    CONSTRAINT ck_models_embedding_dimensions CHECK ((kind = 'EMBEDDING' AND embedding_dimensions IS NOT NULL AND embedding_dimensions > 0) OR (kind = 'LLM' AND embedding_dimensions IS NULL)),
    CONSTRAINT ck_models_default_enabled CHECK (NOT is_default OR enabled),
    CONSTRAINT ck_models_fallback CHECK (NOT is_system_fallback OR (enabled AND kind = 'LLM'))
);
CREATE INDEX ix_models_kind_enabled ON models (kind, enabled);
CREATE UNIQUE INDEX uq_models_default_per_kind ON models (kind) WHERE is_default;
CREATE UNIQUE INDEX uq_models_system_fallback ON models ((TRUE)) WHERE is_system_fallback;
CREATE TRIGGER trg_models_updated_at BEFORE UPDATE ON models FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE models IS 'Platform LLM and embedding models (only enabled ones are offered to users).';

CREATE TABLE system_prompt_templates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    prompt TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_system_prompt_templates PRIMARY KEY (id),
    CONSTRAINT ck_system_prompt_templates_name CHECK (btrim(name) <> '')
);
CREATE TRIGGER trg_system_prompt_templates_updated_at BEFORE UPDATE ON system_prompt_templates FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE system_prompt_templates IS 'Reusable system prompt templates.';

CREATE TABLE advanced_setting_limits (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    setting_key VARCHAR(30) NOT NULL,
    min_value NUMERIC(12,3) NOT NULL,
    max_value NUMERIC(12,3) NOT NULL,
    default_value NUMERIC(12,3) NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_advanced_setting_limits PRIMARY KEY (id),
    CONSTRAINT uq_advanced_setting_limits_key UNIQUE (setting_key),
    CONSTRAINT ck_advanced_setting_limits_key CHECK (setting_key IN ('temperature', 'max_tokens', 'top_k', 'max_context_tokens', 'chunk_size', 'chunk_overlap')),
    CONSTRAINT ck_advanced_setting_limits_range CHECK (min_value <= default_value AND default_value <= max_value)
);
CREATE TRIGGER trg_advanced_setting_limits_updated_at BEFORE UPDATE ON advanced_setting_limits FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE advanced_setting_limits IS 'Admin-defined min, max and default for each advanced chatbot setting. -- ref: identity.users(id) in updated_by';

CREATE TABLE llm_providers (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'OPERATIONAL',
    status_message TEXT,
    checked_at TIMESTAMPTZ,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_llm_providers PRIMARY KEY (id),
    CONSTRAINT uq_llm_providers_provider UNIQUE (provider),
    CONSTRAINT ck_llm_providers_status CHECK (status IN ('OPERATIONAL', 'DEGRADED', 'DOWN'))
);
CREATE TRIGGER trg_llm_providers_updated_at BEFORE UPDATE ON llm_providers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE llm_providers IS 'Current health of each LLM provider.';

CREATE TABLE llm_status_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL,
    from_status VARCHAR(12),
    to_status VARCHAR(12) NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_llm_status_events PRIMARY KEY (id),
    CONSTRAINT fk_llm_status_events_provider FOREIGN KEY (provider) REFERENCES llm_providers (provider) ON DELETE CASCADE,
    CONSTRAINT ck_llm_status_events_to CHECK (to_status IN ('OPERATIONAL', 'DEGRADED', 'DOWN')),
    CONSTRAINT ck_llm_status_events_from CHECK ((from_status IS NULL OR from_status IN ('OPERATIONAL', 'DEGRADED', 'DOWN')))
);
CREATE INDEX ix_llm_status_events_provider ON llm_status_events (provider, created_at DESC);
CREATE TRIGGER trg_llm_status_events_updated_at BEFORE UPDATE ON llm_status_events FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE llm_status_events IS 'History of provider status changes (error alerts).';

-- Seed data. Identifiers must match `ollama list` on the Ollama host.
INSERT INTO models (name, provider, model_identifier, kind, embedding_dimensions, enabled, is_default, is_system_fallback) VALUES
    ('Llama 3.2 3B',     'OLLAMA', 'llama3.2:3b',      'LLM',       NULL, TRUE, TRUE,  FALSE),
    ('Qwen',             'OLLAMA', 'qwen2.5:3b',       'LLM',       NULL, TRUE, FALSE, TRUE),
    ('nomic-embed-text', 'OLLAMA', 'nomic-embed-text', 'EMBEDDING', 768,  TRUE, TRUE,  FALSE),
    ('bge-m3',           'OLLAMA', 'bge-m3',           'EMBEDDING', 1024, TRUE, FALSE, FALSE);
INSERT INTO system_prompt_templates (name, prompt, enabled) VALUES ('Helpful assistant', 'Answer using the provided knowledge.', TRUE);
INSERT INTO advanced_setting_limits (setting_key, min_value, max_value, default_value) VALUES
    ('temperature', 0, 1, 0.4), ('max_tokens', 128, 4096, 1024), ('top_k', 1, 20, 5),
    ('max_context_tokens', 512, 16384, 4096), ('chunk_size', 100, 2000, 500), ('chunk_overlap', 0, 500, 50);
INSERT INTO llm_providers (provider, status) VALUES ('OLLAMA', 'OPERATIONAL');

CREATE TABLE chatbots (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID,
    owner_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200) NOT NULL DEFAULT '',
    avatar TEXT,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    disabled_reason TEXT,
    disabled_at TIMESTAMPTZ,
    disabled_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbots PRIMARY KEY (id),
    CONSTRAINT ck_chatbots_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_chatbots_status CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'PAUSED', 'DISABLED')),
    CONSTRAINT ck_chatbots_disabled CHECK (status <> 'DISABLED' OR (disabled_reason IS NOT NULL AND btrim(disabled_reason) <> '' AND disabled_at IS NOT NULL))
);
CREATE INDEX ix_chatbots_owner_id ON chatbots (owner_id);
CREATE INDEX ix_chatbots_status ON chatbots (status);
CREATE INDEX ix_chatbots_owner_status ON chatbots (owner_id, status);
CREATE INDEX ix_chatbots_created_at ON chatbots (created_at);
CREATE INDEX ix_chatbots_organization_id ON chatbots (organization_id) WHERE organization_id IS NOT NULL;
CREATE TRIGGER trg_chatbots_updated_at BEFORE UPDATE ON chatbots FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbots IS 'Chatbots. -- ref: identity.users(id) in owner_id/disabled_by, identity.organizations(id) in organization_id';

CREATE TABLE chatbot_starter_questions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    position SMALLINT NOT NULL,
    question VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_starter_questions PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_starter_questions_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT uq_chatbot_starter_questions_position UNIQUE (chatbot_id, position),
    CONSTRAINT ck_chatbot_starter_questions_position CHECK (position BETWEEN 1 AND 4),
    CONSTRAINT ck_chatbot_starter_questions_question CHECK (btrim(question) <> '')
);
CREATE TRIGGER trg_chatbot_starter_questions_updated_at BEFORE UPDATE ON chatbot_starter_questions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbot_starter_questions IS 'Ordered starter questions, at most 4 per chatbot.';

CREATE TABLE chatbot_settings (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    model_id UUID,
    model_name VARCHAR(200) NOT NULL,
    fallback_model_id UUID,
    fallback_model_name VARCHAR(200),
    embedding_model VARCHAR(200) NOT NULL DEFAULT 'nomic-embed-text',
    prompt_template_id UUID,
    system_instruction TEXT NOT NULL DEFAULT 'Answer using the provided knowledge.',
    custom_instruction TEXT,
    tone VARCHAR(20) NOT NULL DEFAULT 'PROFESSIONAL',
    answer_length VARCHAR(10) NOT NULL DEFAULT 'CONCISE',
    formatting VARCHAR(5) NOT NULL DEFAULT 'RICH',
    language VARCHAR(4) NOT NULL DEFAULT 'AUTO',
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.4,
    max_tokens INTEGER NOT NULL DEFAULT 1024,
    top_k INTEGER NOT NULL DEFAULT 5,
    search_mode VARCHAR(10) NOT NULL DEFAULT 'HYBRID',
    max_context_tokens INTEGER NOT NULL DEFAULT 4096,
    chunk_size INTEGER NOT NULL DEFAULT 500,
    chunk_overlap INTEGER NOT NULL DEFAULT 50,
    answer_from_documents_only BOOLEAN NOT NULL DEFAULT TRUE,
    show_citations BOOLEAN NOT NULL DEFAULT TRUE,
    welcome_message VARCHAR(2000),
    fallback_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_settings PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_settings_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT uq_chatbot_settings_chatbot_id UNIQUE (chatbot_id),
    CONSTRAINT ck_chatbot_settings_tone CHECK (tone IN ('PROFESSIONAL', 'FRIENDLY', 'CASUAL', 'FORMAL', 'CUSTOM')),
    CONSTRAINT ck_chatbot_settings_answer_length CHECK (answer_length IN ('CONCISE', 'DETAILED')),
    CONSTRAINT ck_chatbot_settings_formatting CHECK (formatting IN ('RICH', 'PLAIN')),
    CONSTRAINT ck_chatbot_settings_language CHECK (language IN ('AUTO', 'EN', 'KM', 'KO')),
    CONSTRAINT ck_chatbot_settings_search_mode CHECK (search_mode IN ('SEMANTIC', 'KEYWORD', 'HYBRID')),
    CONSTRAINT ck_chatbot_settings_temperature CHECK (temperature >= 0 AND temperature <= 1),
    CONSTRAINT ck_chatbot_settings_max_tokens CHECK (max_tokens > 0),
    CONSTRAINT ck_chatbot_settings_top_k CHECK (top_k BETWEEN 1 AND 20),
    CONSTRAINT ck_chatbot_settings_max_context CHECK (max_context_tokens > 0),
    CONSTRAINT ck_chatbot_settings_chunking CHECK (chunk_size > 0 AND chunk_overlap >= 0 AND chunk_overlap < chunk_size),
    CONSTRAINT ck_chatbot_settings_fallback_differs CHECK (fallback_model_name IS NULL OR fallback_model_name <> model_name),
    CONSTRAINT ck_chatbot_settings_custom_tone CHECK (tone <> 'CUSTOM' OR (custom_instruction IS NOT NULL AND btrim(custom_instruction) <> ''))
);
CREATE TRIGGER trg_chatbot_settings_updated_at BEFORE UPDATE ON chatbot_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbot_settings IS '1:1 chatbot configuration. Admin min/max limits (catalog.advanced_setting_limits) are enforced by the application. -- ref: catalog.models(id), catalog.system_prompt_templates(id)';

CREATE FUNCTION chatbot_settings_embedding_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.embedding_model IS DISTINCT FROM OLD.embedding_model THEN
        RAISE EXCEPTION 'The embedding model cannot be changed after the chatbot is created' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_chatbot_settings_embedding_immutable BEFORE UPDATE ON chatbot_settings FOR EACH ROW EXECUTE FUNCTION chatbot_settings_embedding_immutable();

CREATE TABLE chatbot_appeals (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    decision_reason TEXT,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_appeals PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_appeals_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT ck_chatbot_appeals_message CHECK (btrim(message) <> ''),
    CONSTRAINT ck_chatbot_appeals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_chatbot_appeals_decided CHECK (status = 'PENDING' OR decided_at IS NOT NULL),
    CONSTRAINT ck_chatbot_appeals_rejected CHECK (status <> 'REJECTED' OR (decision_reason IS NOT NULL AND btrim(decision_reason) <> ''))
);
CREATE UNIQUE INDEX uq_chatbot_appeals_one_pending ON chatbot_appeals (chatbot_id) WHERE status = 'PENDING';
CREATE INDEX ix_chatbot_appeals_status ON chatbot_appeals (status, created_at);
CREATE INDEX ix_chatbot_appeals_owner_id ON chatbot_appeals (owner_id);
CREATE INDEX ix_chatbot_appeals_chatbot_id ON chatbot_appeals (chatbot_id);
CREATE TRIGGER trg_chatbot_appeals_updated_at BEFORE UPDATE ON chatbot_appeals FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE chatbot_appeals IS 'Owner appeals against a DISABLED chatbot. -- ref: identity.users(id) in owner_id/decided_by';

CREATE TABLE chatbot_status_history (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    from_status VARCHAR(10),
    to_status VARCHAR(10) NOT NULL,
    changed_by UUID,
    actor_role VARCHAR(6) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_chatbot_status_history PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_status_history_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE,
    CONSTRAINT ck_chatbot_status_history_from CHECK ((from_status IS NULL OR from_status IN ('DRAFT', 'PENDING', 'ACTIVE', 'PAUSED', 'DISABLED'))),
    CONSTRAINT ck_chatbot_status_history_to CHECK (to_status IN ('DRAFT', 'PENDING', 'ACTIVE', 'PAUSED', 'DISABLED')),
    CONSTRAINT ck_chatbot_status_history_actor CHECK (actor_role IN ('OWNER', 'ADMIN', 'SYSTEM'))
);
CREATE INDEX ix_chatbot_status_history_chatbot ON chatbot_status_history (chatbot_id, created_at DESC);
COMMENT ON TABLE chatbot_status_history IS 'Append-only history of chatbot status changes. -- ref: identity.users(id) in changed_by';

CREATE TABLE knowledge_sources (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    source_type VARCHAR(10) NOT NULL DEFAULT 'DOCUMENT',
    source_key VARCHAR(512) NOT NULL,
    url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_knowledge_sources PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_sources_key UNIQUE (chatbot_id, source_type, source_key),
    CONSTRAINT ck_knowledge_sources_type CHECK (source_type IN ('DOCUMENT', 'WEBSITE'))
);
CREATE INDEX ix_knowledge_sources_chatbot_id ON knowledge_sources (chatbot_id);
CREATE INDEX ix_knowledge_sources_owner_id ON knowledge_sources (owner_id);
CREATE TRIGGER trg_knowledge_sources_updated_at BEFORE UPDATE ON knowledge_sources FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE knowledge_sources IS 'A document or website a chatbot learns from. -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE documents (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255),
    content_hash CHAR(64),
    status VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
    error_message VARCHAR(2000),
    chunk_count INTEGER NOT NULL DEFAULT 0,
    embedding_model VARCHAR(200),
    chunk_size INTEGER,
    chunk_overlap INTEGER,
    retry_count INTEGER NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_source_id FOREIGN KEY (source_id) REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    CONSTRAINT uq_documents_source_id UNIQUE (source_id),
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_documents_file_type CHECK (file_type IN ('PDF', 'DOCX', 'TXT', 'MD', 'XLSX', 'WEBSITE')),
    CONSTRAINT ck_documents_status CHECK (status IN ('QUEUED', 'UPLOADING', 'PROCESSING', 'CRAWLING', 'EXTRACTING', 'CHUNKING', 'INDEXING', 'READY', 'FAILED', 'REVIEW')),
    CONSTRAINT ck_documents_size CHECK (size_bytes > 0 AND (file_type = 'WEBSITE' OR size_bytes <= 20971520)),
    CONSTRAINT ck_documents_chunk_count CHECK (chunk_count >= 0 AND retry_count >= 0),
    CONSTRAINT ck_documents_failed_message CHECK (status <> 'FAILED' OR error_message IS NOT NULL)
);
CREATE INDEX ix_documents_chatbot_id ON documents (chatbot_id);
CREATE INDEX ix_documents_owner_id ON documents (owner_id);
CREATE INDEX ix_documents_status ON documents (status);
CREATE INDEX ix_documents_chatbot_status ON documents (chatbot_id, status);
CREATE INDEX ix_documents_chatbot_type ON documents (chatbot_id, file_type);
CREATE INDEX ix_documents_content_hash ON documents (content_hash) WHERE content_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_documents_chatbot_name ON documents (chatbot_id, lower(name));
CREATE TRIGGER trg_documents_updated_at BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE documents IS 'Indexed documents (20 MB file limit). -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE document_chunks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    page INTEGER,
    sheet VARCHAR(120),
    row_number INTEGER,
    content TEXT NOT NULL,
    token_count INTEGER,
    vector_id UUID,
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_document_chunks PRIMARY KEY (id),
    CONSTRAINT fk_document_chunks_document_id FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT uq_document_chunks_index UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_document_chunks_index CHECK (chunk_index >= 0)
);
CREATE INDEX ix_document_chunks_tsv ON document_chunks USING GIN (content_tsv);
CREATE INDEX ix_document_chunks_vector_id ON document_chunks (vector_id) WHERE vector_id IS NOT NULL;
CREATE TRIGGER trg_document_chunks_updated_at BEFORE UPDATE ON document_chunks FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE document_chunks IS 'Text chunks used for retrieval; content_tsv powers keyword search.';

CREATE TABLE ingestion_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    status VARCHAR(12) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_ingestion_jobs PRIMARY KEY (id),
    CONSTRAINT fk_ingestion_jobs_document_id FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT ck_ingestion_jobs_status CHECK (status IN ('QUEUED', 'UPLOADING', 'PROCESSING', 'CRAWLING', 'EXTRACTING', 'CHUNKING', 'INDEXING', 'READY', 'FAILED', 'REVIEW')),
    CONSTRAINT ck_ingestion_jobs_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_ingestion_jobs_document_id ON ingestion_jobs (document_id);
CREATE TRIGGER trg_ingestion_jobs_updated_at BEFORE UPDATE ON ingestion_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE ingestion_jobs IS 'Processing attempts for a document.';

CREATE TABLE crawl_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    start_url VARCHAR(2048) NOT NULL,
    max_depth SMALLINT NOT NULL,
    max_pages SMALLINT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PREVIEW',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_crawl_jobs PRIMARY KEY (id),
    CONSTRAINT ck_crawl_jobs_depth CHECK (max_depth BETWEEN 1 AND 3),
    CONSTRAINT ck_crawl_jobs_pages CHECK (max_pages BETWEEN 50 AND 100),
    CONSTRAINT ck_crawl_jobs_status CHECK (status IN ('PREVIEW', 'IMPORTING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX ix_crawl_jobs_chatbot_id ON crawl_jobs (chatbot_id);
CREATE INDEX ix_crawl_jobs_owner_id ON crawl_jobs (owner_id);
CREATE TRIGGER trg_crawl_jobs_updated_at BEFORE UPDATE ON crawl_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE crawl_jobs IS 'Site crawl: preview first, then import. -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE crawl_pages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    crawl_job_id UUID NOT NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500),
    content_preview TEXT,
    depth SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(8) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    document_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_crawl_pages PRIMARY KEY (id),
    CONSTRAINT fk_crawl_pages_crawl_job_id FOREIGN KEY (crawl_job_id) REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_crawl_pages_document_id FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE SET NULL,
    CONSTRAINT uq_crawl_pages_job_url UNIQUE (crawl_job_id, url),
    CONSTRAINT ck_crawl_pages_status CHECK (status IN ('OK', 'EMPTY', 'BLOCKED', 'FAILED')),
    CONSTRAINT ck_crawl_pages_depth CHECK (depth >= 0)
);
CREATE INDEX ix_crawl_pages_document_id ON crawl_pages (document_id) WHERE document_id IS NOT NULL;
CREATE TRIGGER trg_crawl_pages_updated_at BEFORE UPDATE ON crawl_pages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE crawl_pages IS 'Pages found by a crawl, previewed before import.';

CREATE TABLE vector_cleanup_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    document_id UUID,
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_vector_cleanup_jobs PRIMARY KEY (id),
    CONSTRAINT ck_vector_cleanup_jobs_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_vector_cleanup_jobs_next_attempt_at ON vector_cleanup_jobs (next_attempt_at);
CREATE TRIGGER trg_vector_cleanup_jobs_updated_at BEFORE UPDATE ON vector_cleanup_jobs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE vector_cleanup_jobs IS 'Deferred Qdrant cleanup. Deliberately NO foreign keys: the task must outlive the deleted rows.';

CREATE VIEW v_owner_storage_usage AS
    SELECT owner_id, SUM(size_bytes)::bigint AS bytes_used, COUNT(*)::bigint AS documents
    FROM documents
    GROUP BY owner_id;
COMMENT ON VIEW v_owner_storage_usage IS 'Storage used by each owner, for quota checks.';

CREATE TABLE conversations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL DEFAULT 'PLAYGROUND',
    integration_id UUID,
    session_hash VARCHAR(64),
    title VARCHAR(200) NOT NULL DEFAULT 'New conversation',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT ck_conversations_playground_only CHECK (channel = 'PLAYGROUND')
);
CREATE INDEX ix_conversations_chatbot_updated ON conversations (chatbot_id, updated_at DESC);
CREATE INDEX ix_conversations_owner_id ON conversations (owner_id);
CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON conversations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE conversations IS 'Playground chats only. V5 never stores guest conversations (integration_id and session_hash are kept for compatibility). -- ref: chatbot.chatbots(id), identity.users(id), channel.channel_integrations(id)';

CREATE TABLE messages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    model_name VARCHAR(200),
    tokens_in INTEGER,
    tokens_out INTEGER,
    latency_ms INTEGER,
    unanswered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT uq_messages_sequence UNIQUE (conversation_id, sequence),
    CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_messages_tokens CHECK ((tokens_in IS NULL OR tokens_in >= 0) AND (tokens_out IS NULL OR tokens_out >= 0) AND (latency_ms IS NULL OR latency_ms >= 0))
);
CREATE TRIGGER trg_messages_updated_at BEFORE UPDATE ON messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE messages IS 'Messages of a playground conversation.';

CREATE TABLE message_sources (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    document_id UUID,
    chunk_id UUID,
    document_name VARCHAR(255) NOT NULL,
    excerpt TEXT NOT NULL,
    page INTEGER,
    sheet VARCHAR(120),
    row_number INTEGER,
    chunk_index INTEGER,
    score DOUBLE PRECISION NOT NULL,
    url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_message_sources PRIMARY KEY (id),
    CONSTRAINT fk_message_sources_message_id FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE
);
CREATE INDEX ix_message_sources_message_id ON message_sources (message_id);
CREATE TRIGGER trg_message_sources_updated_at BEFORE UPDATE ON message_sources FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE message_sources IS 'Citations. document_id and chunk_id are plain references (no FK) so history survives re-indexing. -- ref: knowledge.documents(id), knowledge.document_chunks(id)';

CREATE TABLE answer_feedback (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    integration_id UUID,
    answer_ref VARCHAR(64) NOT NULL,
    kind VARCHAR(6) NOT NULL,
    rating VARCHAR(11),
    reason VARCHAR(10),
    comment VARCHAR(500),
    question TEXT,
    answer TEXT,
    sources JSONB,
    status VARCHAR(9),
    handled_by UUID,
    handled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_answer_feedback PRIMARY KEY (id),
    CONSTRAINT uq_answer_feedback_answer_ref UNIQUE (answer_ref),
    CONSTRAINT ck_answer_feedback_channel CHECK (channel IN ('PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM')),
    CONSTRAINT ck_answer_feedback_kind CHECK (kind IN ('RATING', 'REPORT')),
    CONSTRAINT ck_answer_feedback_rating CHECK ((rating IS NULL OR rating IN ('HELPFUL', 'NOT_HELPFUL'))),
    CONSTRAINT ck_answer_feedback_reason CHECK ((reason IS NULL OR reason IN ('INACCURATE', 'UNHELPFUL'))),
    CONSTRAINT ck_answer_feedback_status CHECK ((status IS NULL OR status IN ('OPEN', 'RESOLVED', 'DISMISSED'))),
    CONSTRAINT ck_answer_feedback_rating_shape CHECK (kind <> 'RATING' OR (rating IS NOT NULL AND reason IS NULL AND comment IS NULL AND question IS NULL AND answer IS NULL AND sources IS NULL AND status IS NULL AND handled_by IS NULL AND handled_at IS NULL)),
    CONSTRAINT ck_answer_feedback_report_shape CHECK (kind <> 'REPORT' OR (reason IS NOT NULL AND question IS NOT NULL AND answer IS NOT NULL AND status IS NOT NULL AND rating IS NULL)),
    CONSTRAINT ck_answer_feedback_handled CHECK (status IS NULL OR status = 'OPEN' OR handled_at IS NOT NULL)
);
CREATE INDEX ix_answer_feedback_chatbot_status ON answer_feedback (chatbot_id, status, created_at DESC);
CREATE INDEX ix_answer_feedback_chatbot_reason ON answer_feedback (chatbot_id, reason);
CREATE INDEX ix_answer_feedback_kind ON answer_feedback (kind);
CREATE INDEX ix_answer_feedback_owner_id ON answer_feedback (owner_id);
CREATE TRIGGER trg_answer_feedback_updated_at BEFORE UPDATE ON answer_feedback FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE answer_feedback IS 'Guest answer ratings and reports. One feedback per answer; only reports keep the question and answer. -- ref: chatbot.chatbots(id), identity.users(id), channel.channel_integrations(id)';

CREATE TABLE channel_integrations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    public_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(12) NOT NULL DEFAULT 'OFF',
    error_reason TEXT,
    settings TEXT,
    expires_at TIMESTAMPTZ,
    access_password_hash VARCHAR(255),
    regenerated_at TIMESTAMPTZ,
    credential TEXT,
    token_mask VARCHAR(40),
    webhook_secret VARCHAR(64),
    telegram_bot_id VARCHAR(40),
    telegram_username VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_channel_integrations PRIMARY KEY (id),
    CONSTRAINT uq_channel_integrations_public_id UNIQUE (public_id),
    CONSTRAINT uq_channel_integrations_chatbot_channel UNIQUE (chatbot_id, channel),
    CONSTRAINT ck_channel_integrations_channel CHECK (channel IN ('PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM')),
    CONSTRAINT ck_channel_integrations_status CHECK (status IN ('LIVE', 'OFF', 'CONNECTED', 'DISCONNECTED', 'ERROR')),
    CONSTRAINT ck_channel_integrations_error CHECK (status <> 'ERROR' OR error_reason IS NOT NULL)
);
CREATE INDEX ix_channel_integrations_chatbot_id ON channel_integrations (chatbot_id);
CREATE INDEX ix_channel_integrations_owner_id ON channel_integrations (owner_id);
CREATE INDEX ix_channel_integrations_status ON channel_integrations (status);
CREATE UNIQUE INDEX uq_channel_integrations_telegram_bot ON channel_integrations (telegram_bot_id) WHERE telegram_bot_id IS NOT NULL;
CREATE TRIGGER trg_channel_integrations_updated_at BEFORE UPDATE ON channel_integrations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE channel_integrations IS 'Public link, web widget and Telegram per chatbot. credential is ENCRYPTED, access_password_hash is HASHED. -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE channel_allowed_domains (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    domain VARCHAR(253) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_channel_allowed_domains PRIMARY KEY (id),
    CONSTRAINT fk_channel_allowed_domains_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE CASCADE,
    CONSTRAINT uq_channel_allowed_domains UNIQUE (integration_id, domain),
    CONSTRAINT ck_channel_allowed_domains_domain CHECK (domain = lower(domain) AND domain ~ '^([a-z0-9-]+\.)*[a-z0-9-]+(:[0-9]+)?$')
);
CREATE TRIGGER trg_channel_allowed_domains_updated_at BEFORE UPDATE ON channel_allowed_domains FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE channel_allowed_domains IS 'Websites allowed to embed a web widget.';

CREATE TABLE retired_public_ids (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    public_id VARCHAR(64) NOT NULL,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    reason VARCHAR(16) NOT NULL,
    retired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_retired_public_ids PRIMARY KEY (id),
    CONSTRAINT uq_retired_public_ids_public_id UNIQUE (public_id),
    CONSTRAINT ck_retired_public_ids_reason CHECK (reason IN ('CHATBOT_DELETED', 'REGENERATED', 'CHANNEL_REMOVED'))
);
CREATE INDEX ix_retired_public_ids_chatbot_id ON retired_public_ids (chatbot_id);
CREATE TRIGGER trg_retired_public_ids_updated_at BEFORE UPDATE ON retired_public_ids FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE retired_public_ids IS 'Old public tokens, so guests see the right message (CHATBOT_DELETED shows ''This chatbot is no longer available!''). -- ref: chatbot.chatbots(id), identity.users(id)';

CREATE TABLE telegram_updates (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    update_id BIGINT NOT NULL,
    answer TEXT,
    sent_parts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_telegram_updates PRIMARY KEY (id),
    CONSTRAINT fk_telegram_updates_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE CASCADE,
    CONSTRAINT uq_telegram_updates_update UNIQUE (integration_id, update_id),
    CONSTRAINT ck_telegram_updates_parts CHECK (sent_parts >= 0)
);
CREATE TRIGGER trg_telegram_updates_updated_at BEFORE UPDATE ON telegram_updates FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE telegram_updates IS 'Telegram webhook idempotency records.';

CREATE TABLE channel_messages (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    integration_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_channel_messages PRIMARY KEY (id),
    CONSTRAINT fk_channel_messages_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE CASCADE
);
CREATE INDEX ix_channel_messages_integration_time ON channel_messages (integration_id, created_at);
CREATE TRIGGER trg_channel_messages_updated_at BEFORE UPDATE ON channel_messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE channel_messages IS 'One row per guest exchange, for ''messages this week'' per channel.';

CREATE TABLE analytics_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    messages INTEGER NOT NULL DEFAULT 2,
    occurred_at TIMESTAMPTZ NOT NULL,
    session_id VARCHAR(64),
    guest_id VARCHAR(64),
    question TEXT,
    question_hash CHAR(64),
    unanswered BOOLEAN NOT NULL DEFAULT FALSE,
    tokens_in INTEGER NOT NULL DEFAULT 0,
    tokens_out INTEGER NOT NULL DEFAULT 0,
    model_name VARCHAR(200),
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_analytics_events PRIMARY KEY (id),
    CONSTRAINT ck_analytics_events_channel CHECK (channel IN ('PLAYGROUND', 'PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM')),
    CONSTRAINT ck_analytics_events_counts CHECK (messages >= 0 AND tokens_in >= 0 AND tokens_out >= 0 AND (latency_ms IS NULL OR latency_ms >= 0))
);
CREATE INDEX ix_analytics_events_owner_time ON analytics_events (owner_id, occurred_at);
CREATE INDEX ix_analytics_events_chatbot_time ON analytics_events (chatbot_id, occurred_at);
CREATE INDEX ix_analytics_events_time ON analytics_events (occurred_at);
CREATE INDEX ix_analytics_events_chatbot_session ON analytics_events (chatbot_id, session_id) WHERE session_id IS NOT NULL;
CREATE INDEX ix_analytics_events_unanswered ON analytics_events (chatbot_id, question_hash) WHERE unanswered;
CREATE INDEX ix_analytics_events_question ON analytics_events (chatbot_id, question_hash) WHERE question_hash IS NOT NULL;
CREATE TRIGGER trg_analytics_events_updated_at BEFORE UPDATE ON analytics_events FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE analytics_events IS 'One row per question/answer exchange. question is kept for most-asked and unanswered reports (a privacy trade-off); guests are anonymous ids. -- ref: identity.users(id), chatbot.chatbots(id)';

CREATE VIEW v_chatbot_daily AS
    SELECT (occurred_at AT TIME ZONE 'UTC')::date AS day, chatbot_id, owner_id, channel,
           SUM(messages)::bigint AS messages, COUNT(DISTINCT session_id) AS sessions, COUNT(DISTINCT guest_id) AS guests,
           COUNT(*) FILTER (WHERE unanswered) AS unanswered_count, SUM(tokens_in + tokens_out)::bigint AS tokens
    FROM analytics_events
    GROUP BY 1, 2, 3, 4;
COMMENT ON VIEW v_chatbot_daily IS 'Daily messages, sessions, guests, unanswered questions and tokens per chatbot and channel. Active chat sessions = distinct session_id with an event in the last 30 minutes (query analytics_events).';

CREATE VIEW v_owner_token_usage AS
    SELECT owner_id, date_trunc('month', occurred_at AT TIME ZONE 'UTC')::date AS month, SUM(tokens_in + tokens_out)::bigint AS tokens
    FROM analytics_events
    GROUP BY 1, 2;
COMMENT ON VIEW v_owner_token_usage IS 'Monthly token usage per owner (top users by token usage).';

CREATE VIEW v_platform_daily AS
    SELECT (occurred_at AT TIME ZONE 'UTC')::date AS day, SUM(messages)::bigint AS messages, COUNT(DISTINCT chatbot_id) AS active_chatbots, SUM(tokens_in + tokens_out)::bigint AS tokens
    FROM analytics_events
    GROUP BY 1;
COMMENT ON VIEW v_platform_daily IS 'Platform-wide daily message volume.';

CREATE TABLE api_keys (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_api_keys PRIMARY KEY (id),
    CONSTRAINT fk_api_keys_organization_id FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash)
);
CREATE INDEX ix_api_keys_organization_id ON api_keys (organization_id);
CREATE TRIGGER trg_api_keys_updated_at BEFORE UPDATE ON api_keys FOR EACH ROW EXECUTE FUNCTION set_updated_at();
COMMENT ON TABLE api_keys IS 'Organization API keys (hashed).';

-- audit_logs: keep the legacy FastAPI columns so older code can be migrated, and allow free-form actions such as 'chatbot.created'.
ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;
ALTER TABLE audit_logs ADD COLUMN organization_id UUID, ADD COLUMN resource_type VARCHAR(80);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_organization_id FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL;
CREATE INDEX ix_audit_logs_organization_id ON audit_logs (organization_id) WHERE organization_id IS NOT NULL;

-- Cross-service references from the microservice schemas become real foreign keys here.
-- (message_sources.document_id, retired_public_ids.* and vector_cleanup_jobs.* stay plain UUIDs on purpose.)
ALTER TABLE chatbots ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE chatbots ADD CONSTRAINT fk_chatbots_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE chatbots ADD CONSTRAINT fk_chatbots_organization_id FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE;
ALTER TABLE chatbots ADD CONSTRAINT fk_chatbots_disabled_by FOREIGN KEY (disabled_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX ix_chatbots_disabled_by ON chatbots (disabled_by);
ALTER TABLE chatbot_settings ADD CONSTRAINT fk_chatbot_settings_model_id FOREIGN KEY (model_id) REFERENCES models (id) ON DELETE RESTRICT;
CREATE INDEX ix_chatbot_settings_model_id ON chatbot_settings (model_id);
ALTER TABLE chatbot_settings ADD CONSTRAINT fk_chatbot_settings_fallback_model_id FOREIGN KEY (fallback_model_id) REFERENCES models (id) ON DELETE SET NULL;
CREATE INDEX ix_chatbot_settings_fallback_model_id ON chatbot_settings (fallback_model_id);
ALTER TABLE chatbot_settings ADD CONSTRAINT fk_chatbot_settings_prompt_template_id FOREIGN KEY (prompt_template_id) REFERENCES system_prompt_templates (id) ON DELETE SET NULL;
CREATE INDEX ix_chatbot_settings_prompt_template_id ON chatbot_settings (prompt_template_id);
ALTER TABLE chatbot_appeals ADD CONSTRAINT fk_chatbot_appeals_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE chatbot_appeals ADD CONSTRAINT fk_chatbot_appeals_decided_by FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX ix_chatbot_appeals_decided_by ON chatbot_appeals (decided_by);
ALTER TABLE chatbot_status_history ADD CONSTRAINT fk_chatbot_status_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX ix_chatbot_status_history_changed_by ON chatbot_status_history (changed_by);
ALTER TABLE knowledge_sources ADD CONSTRAINT fk_knowledge_sources_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE knowledge_sources ADD CONSTRAINT fk_knowledge_sources_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE documents ADD CONSTRAINT fk_documents_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE documents ADD CONSTRAINT fk_documents_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE crawl_jobs ADD CONSTRAINT fk_crawl_jobs_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE crawl_jobs ADD CONSTRAINT fk_crawl_jobs_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE SET NULL;
CREATE INDEX ix_conversations_integration_id ON conversations (integration_id);
ALTER TABLE message_sources ADD CONSTRAINT fk_message_sources_chunk_id FOREIGN KEY (chunk_id) REFERENCES document_chunks (id) ON DELETE SET NULL;
CREATE INDEX ix_message_sources_chunk_id ON message_sources (chunk_id);
ALTER TABLE answer_feedback ADD CONSTRAINT fk_answer_feedback_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE answer_feedback ADD CONSTRAINT fk_answer_feedback_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE answer_feedback ADD CONSTRAINT fk_answer_feedback_integration_id FOREIGN KEY (integration_id) REFERENCES channel_integrations (id) ON DELETE SET NULL;
CREATE INDEX ix_answer_feedback_integration_id ON answer_feedback (integration_id);
ALTER TABLE answer_feedback ADD CONSTRAINT fk_answer_feedback_handled_by FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX ix_answer_feedback_handled_by ON answer_feedback (handled_by);
ALTER TABLE channel_integrations ADD CONSTRAINT fk_channel_integrations_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE channel_integrations ADD CONSTRAINT fk_channel_integrations_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE analytics_events ADD CONSTRAINT fk_analytics_events_chatbot_id FOREIGN KEY (chatbot_id) REFERENCES chatbots (id) ON DELETE CASCADE;
ALTER TABLE analytics_events ADD CONSTRAINT fk_analytics_events_owner_id FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE advanced_setting_limits ADD CONSTRAINT fk_advanced_setting_limits_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX ix_advanced_setting_limits_updated_by ON advanced_setting_limits (updated_by);
ALTER TABLE llm_providers ADD CONSTRAINT fk_llm_providers_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX ix_llm_providers_updated_by ON llm_providers (updated_by);

CREATE VIEW v_owner_quota_usage AS
    SELECT u.id AS user_id,
           COALESCE(q.max_bots, d.max_bots) AS max_bots,
           (SELECT COUNT(*) FROM chatbots c WHERE c.owner_id = u.id) AS bots_used,
           COALESCE(q.max_storage_bytes, d.max_storage_bytes) AS max_storage_bytes,
           COALESCE((SELECT SUM(doc.size_bytes) FROM documents doc WHERE doc.owner_id = u.id), 0)::bigint AS storage_used
    FROM users u
    LEFT JOIN user_quotas q ON q.user_id = u.id
    CROSS JOIN quota_defaults d;
COMMENT ON VIEW v_owner_quota_usage IS 'Quota limits and current usage per user.';

-- Qdrant (not SQL): one collection (env QDRANT_COLLECTION, default rag_craft); vector size = catalog.models.embedding_dimensions of the chatbot's embedding model;
-- distance Cosine; point id = knowledge.document_chunks.vector_id; payload: chatbot_id, owner_id, document_id, chunk_id, chunk_index, page, sheet, row_number, embedding_model.
-- Every search must filter by chatbot_id AND owner_id AND embedding_model, then be re-checked against Postgres (document status READY).

COMMIT;
