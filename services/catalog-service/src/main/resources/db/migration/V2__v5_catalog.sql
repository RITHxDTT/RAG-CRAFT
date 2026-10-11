-- V5 catalog changes. Portable: runs on H2 (PostgreSQL mode, tests) and on PostgreSQL. The hosted PostgreSQL schema comes from database/craftrag_schema.sql.
ALTER TABLE models ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'LLM';
ALTER TABLE models ADD COLUMN embedding_dimensions INTEGER;
ALTER TABLE models ADD COLUMN is_system_fallback BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE advanced_setting_limits (
    id UUID PRIMARY KEY,
    setting_key VARCHAR(30) NOT NULL UNIQUE,
    min_value DECIMAL(12,3) NOT NULL,
    max_value DECIMAL(12,3) NOT NULL,
    default_value DECIMAL(12,3) NOT NULL,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
