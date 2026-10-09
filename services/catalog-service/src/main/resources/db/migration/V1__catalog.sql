-- catalog-service owns the platform model and prompt template catalog.
CREATE TABLE models (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    model_identifier VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE system_prompt_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    prompt TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
