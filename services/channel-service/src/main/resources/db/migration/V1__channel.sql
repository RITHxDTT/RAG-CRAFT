-- channel-service owns deployment channels. chatbot_id and owner_id reference other services by id only.
CREATE TABLE channel_integrations (
    id UUID PRIMARY KEY,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(12) NOT NULL,
    settings TEXT,
    credential TEXT,
    webhook_secret VARCHAR(64),
    telegram_bot_id VARCHAR(40),
    telegram_username VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_channel_per_bot UNIQUE (chatbot_id, channel)
);

CREATE TABLE telegram_updates (
    id UUID PRIMARY KEY,
    integration_id UUID NOT NULL REFERENCES channel_integrations(id) ON DELETE CASCADE,
    update_id BIGINT NOT NULL,
    answer TEXT,
    sent_parts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_telegram_update UNIQUE (integration_id, update_id)
);

CREATE TABLE channel_messages (
    id UUID PRIMARY KEY,
    integration_id UUID NOT NULL REFERENCES channel_integrations(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_channels_chatbot ON channel_integrations(chatbot_id);
CREATE INDEX ix_channels_owner ON channel_integrations(owner_id);
CREATE INDEX ix_channel_messages_integration ON channel_messages(integration_id, created_at);
