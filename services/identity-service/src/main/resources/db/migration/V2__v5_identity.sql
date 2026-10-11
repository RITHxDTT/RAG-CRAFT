-- V5 identity changes. Portable: runs on H2 (PostgreSQL mode, tests) and on PostgreSQL. The hosted PostgreSQL schema comes from database/craftrag_schema.sql
-- (it adds CHECK constraints, partial indexes, the generated is_active column and the append-only audit trigger on top of this).
ALTER TABLE users ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'en';
ALTER TABLE users ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN signup_method VARCHAR(10) NOT NULL DEFAULT 'EMAIL';
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN lock_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN suspend_reason TEXT;
ALTER TABLE users ADD COLUMN suspended_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN suspended_by UUID;
ALTER TABLE users ADD COLUMN pending_email VARCHAR(254);
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMP WITH TIME ZONE;
UPDATE users SET status = 'SUSPENDED', suspend_reason = 'Disabled before the V5 upgrade.' WHERE is_active = FALSE;

CREATE TABLE quota_defaults (
    id UUID PRIMARY KEY,
    singleton BOOLEAN NOT NULL DEFAULT TRUE UNIQUE,
    max_bots INTEGER NOT NULL DEFAULT 5,
    max_storage_bytes BIGINT NOT NULL DEFAULT 5368709120,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO quota_defaults (id, singleton, max_bots, max_storage_bytes) VALUES (gen_random_uuid(), TRUE, 5, 5368709120);

CREATE TABLE user_quotas (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    max_bots INTEGER NOT NULL,
    max_storage_bytes BIGINT NOT NULL,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE email_verifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(15) NOT NULL,
    email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    admin_email VARCHAR(254) NOT NULL,
    action VARCHAR(30) NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id UUID NOT NULL,
    target_label VARCHAR(254) NOT NULL,
    reason TEXT NOT NULL,
    details JSONB,
    source_service VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_logs_created_at ON audit_logs(created_at);
