-- knowledge-service owns everything about documents. chatbot_id and owner_id are ids from other services.
CREATE TABLE knowledge_sources (
    id UUID PRIMARY KEY,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    source_type VARCHAR(10) NOT NULL,
    source_key VARCHAR(255) NOT NULL,
    url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL UNIQUE REFERENCES knowledge_sources(id) ON DELETE CASCADE,
    chatbot_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255),
    status VARCHAR(12) NOT NULL,
    error_message VARCHAR(2000),
    chunk_count INTEGER NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    page INTEGER,
    sheet VARCHAR(120),
    row_number INTEGER,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    status VARCHAR(12) NOT NULL,
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_documents_chatbot ON documents(chatbot_id);
CREATE INDEX ix_documents_owner ON documents(owner_id);
CREATE INDEX ix_documents_status ON documents(status);
CREATE INDEX ix_chunks_document ON document_chunks(document_id, chunk_index);
CREATE INDEX ix_jobs_document ON ingestion_jobs(document_id);
