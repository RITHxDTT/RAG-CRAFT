-- V5 chatbot changes. Portable: runs on H2 (PostgreSQL mode, tests) and on PostgreSQL. The hosted PostgreSQL schema comes from database/craftrag_schema.sql
-- (it adds CHECK constraints, the append-only guards and the immutable embedding-model trigger on top of this).
ALTER TABLE chatbots ADD COLUMN disabled_reason TEXT;
ALTER TABLE chatbots ADD COLUMN disabled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE chatbots ADD COLUMN disabled_by UUID;
UPDATE chatbots SET status = 'PAUSED' WHERE status IN ('INACTIVE', 'ERROR');

ALTER TABLE chatbot_settings ADD COLUMN fallback_model_id UUID;
ALTER TABLE chatbot_settings ADD COLUMN fallback_model_name VARCHAR(200);
ALTER TABLE chatbot_settings ADD COLUMN embedding_model VARCHAR(200) NOT NULL DEFAULT 'nomic-embed-text';
ALTER TABLE chatbot_settings ADD COLUMN formatting VARCHAR(5) NOT NULL DEFAULT 'RICH';
ALTER TABLE chatbot_settings ADD COLUMN language VARCHAR(4) NOT NULL DEFAULT 'AUTO';
ALTER TABLE chatbot_settings ADD COLUMN max_tokens INTEGER NOT NULL DEFAULT 1024;
ALTER TABLE chatbot_settings ADD COLUMN search_mode VARCHAR(10) NOT NULL DEFAULT 'HYBRID';
ALTER TABLE chatbot_settings ADD COLUMN max_context_tokens INTEGER NOT NULL DEFAULT 4096;
ALTER TABLE chatbot_settings ADD COLUMN chunk_size INTEGER NOT NULL DEFAULT 500;
ALTER TABLE chatbot_settings ADD COLUMN chunk_overlap INTEGER NOT NULL DEFAULT 50;
ALTER TABLE chatbot_settings ADD COLUMN answer_from_documents_only BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE chatbot_settings SET answer_length = CASE WHEN answer_length = 'LONG' THEN 'DETAILED' ELSE 'CONCISE' END;
UPDATE chatbot_settings SET tone = CASE tone WHEN 'CONCISE' THEN 'PROFESSIONAL' WHEN 'EDUCATIONAL' THEN 'FRIENDLY' WHEN 'DETAILED' THEN 'PROFESSIONAL' ELSE tone END;

CREATE TABLE chatbot_starter_questions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    chatbot_id UUID NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    position SMALLINT NOT NULL,
    question VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chatbot_starter_questions_position UNIQUE (chatbot_id, position)
);

CREATE TABLE chatbot_appeals (
    id UUID PRIMARY KEY,
    chatbot_id UUID NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    decision_reason TEXT,
    decided_by UUID,
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chatbot_status_history (
    id UUID PRIMARY KEY,
    chatbot_id UUID NOT NULL REFERENCES chatbots(id) ON DELETE CASCADE,
    from_status VARCHAR(10),
    to_status VARCHAR(10) NOT NULL,
    changed_by UUID,
    actor_role VARCHAR(6) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
