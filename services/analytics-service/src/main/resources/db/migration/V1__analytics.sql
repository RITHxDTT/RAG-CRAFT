-- analytics-service owns the message event log. Everything else it reports is read live from the other services.
CREATE TABLE analytics_events (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    chatbot_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    messages INTEGER NOT NULL DEFAULT 2,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_events_owner_time ON analytics_events(owner_id, occurred_at);
CREATE INDEX ix_events_time ON analytics_events(occurred_at);
