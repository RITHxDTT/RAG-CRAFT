-- chatbot-service owns chatbots and their settings. owner_id / organization_id reference identity-service by id only.
CREATE TABLE chatbots (
    id UUID PRIMARY KEY,
    organization_id UUID,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    avatar VARCHAR(16),
    status VARCHAR(10) NOT NULL,
    starter_questions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE chatbot_settings (
    id UUID PRIMARY KEY,
    chatbot_id UUID NOT NULL UNIQUE REFERENCES chatbots(id) ON DELETE CASCADE,
    model_id UUID,
    model_name VARCHAR(200) NOT NULL,
    prompt_template_id UUID,
    system_instruction TEXT NOT NULL,
    custom_instruction TEXT,
    tone VARCHAR(20) NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    answer_length VARCHAR(10) NOT NULL,
    top_k INTEGER NOT NULL,
    welcome_message VARCHAR(2000),
    fallback_message VARCHAR(2000),
    show_citations BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_chatbots_owner ON chatbots(owner_id);
CREATE INDEX ix_chatbots_status ON chatbots(status);
