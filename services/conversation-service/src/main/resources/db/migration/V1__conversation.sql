-- conversation-service owns chat history for every channel. chatbot_id, owner_id, integration_id, document_id
-- and chunk_id are references to other services and are deliberately not foreign keys.
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    channel VARCHAR(12) NOT NULL,
    integration_id UUID,
    session_hash VARCHAR(64),
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_message_sequence UNIQUE (conversation_id, sequence)
);

CREATE TABLE message_sources (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_conversations_chatbot ON conversations(chatbot_id, updated_at);
CREATE INDEX ix_conversations_owner ON conversations(owner_id);
CREATE INDEX ix_conversations_session ON conversations(integration_id, session_hash);
CREATE INDEX ix_messages_conversation ON messages(conversation_id, sequence);
CREATE INDEX ix_sources_message ON message_sources(message_id);
