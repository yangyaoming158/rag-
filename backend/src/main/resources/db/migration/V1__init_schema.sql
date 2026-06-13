CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN'))
);

CREATE TABLE knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(128) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_knowledge_bases_owner_name UNIQUE (owner_id, name)
);
CREATE INDEX idx_knowledge_bases_owner_id ON knowledge_bases(owner_id);

CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_status CHECK (status IN ('UPLOADED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'READY', 'FAILED')),
    CONSTRAINT uq_documents_kb_sha256 UNIQUE (kb_id, sha256)
);
CREATE INDEX idx_documents_kb_status ON documents(kb_id, status);

CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    heading_path VARCHAR(1000),
    page_start INTEGER,
    page_end INTEGER,
    char_len INTEGER NOT NULL,
    embedding vector(1024),
    embedding_model VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_chunks_document_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_document_chunks_kb_id ON document_chunks(kb_id);
CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_document_chunks_embedding_hnsw ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_user_kb ON conversations(user_id, kb_id);

CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_messages_status CHECK (status IN ('OK', 'NO_ANSWER', 'UNGROUNDED', 'ERROR'))
);
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);

CREATE TABLE citations (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    chunk_id BIGINT REFERENCES document_chunks(id) ON DELETE SET NULL,
    rank INTEGER NOT NULL,
    similarity DOUBLE PRECISION NOT NULL,
    snippet VARCHAR(500) NOT NULL,
    document_filename VARCHAR(255) NOT NULL
);
CREATE INDEX idx_citations_message_id ON citations(message_id);

CREATE TABLE ingestion_jobs (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    phase VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 0,
    max_attempt INTEGER NOT NULL DEFAULT 3,
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ingestion_jobs_phase CHECK (phase IN ('PARSE', 'CHUNK', 'EMBED')),
    CONSTRAINT chk_ingestion_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX idx_ingestion_jobs_status ON ingestion_jobs(status);
CREATE INDEX idx_ingestion_jobs_document_id ON ingestion_jobs(document_id);

CREATE TABLE model_call_logs (
    id BIGSERIAL PRIMARY KEY,
    call_type VARCHAR(16) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    message_id BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    document_id BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    latency_ms BIGINT,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_model_call_logs_type CHECK (call_type IN ('CHAT', 'EMBEDDING')),
    CONSTRAINT chk_model_call_logs_status CHECK (status IN ('OK', 'ERROR'))
);
CREATE INDEX idx_model_call_logs_type_created ON model_call_logs(call_type, created_at);

INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2b$10$xsQ6xMWWvL1E.7V5KP6Uzerox11R8xCs6Bu7S18us99Xna3l2BSJW', 'ADMIN');
