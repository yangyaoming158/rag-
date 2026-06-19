CREATE TABLE review_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    review_type VARCHAR(64) NOT NULL,
    supplement TEXT,
    status VARCHAR(32) NOT NULL,
    conclusion TEXT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    issues TEXT NOT NULL,
    suggestions TEXT NOT NULL,
    citation_warning VARCHAR(500),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_review_reports_type CHECK (review_type IN ('PRD_API_CONSISTENCY', 'TASK_TREE_RISK')),
    CONSTRAINT chk_review_reports_status CHECK (status IN ('OK', 'NO_ANSWER', 'UNGROUNDED', 'ERROR')),
    CONSTRAINT chk_review_reports_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'))
);
CREATE INDEX idx_review_reports_user_kb_created ON review_reports(user_id, kb_id, created_at DESC);

CREATE TABLE review_citations (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES review_reports(id) ON DELETE CASCADE,
    chunk_id BIGINT REFERENCES document_chunks(id) ON DELETE SET NULL,
    rank INTEGER NOT NULL,
    similarity DOUBLE PRECISION NOT NULL,
    snippet VARCHAR(500) NOT NULL,
    document_filename VARCHAR(255) NOT NULL,
    heading_path VARCHAR(1000)
);
CREATE INDEX idx_review_citations_review_id ON review_citations(review_id);
