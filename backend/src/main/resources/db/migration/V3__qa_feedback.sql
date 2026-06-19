CREATE TABLE qa_feedback (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating VARCHAR(32) NOT NULL,
    reason VARCHAR(120),
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_qa_feedback_message_user UNIQUE (message_id, user_id),
    CONSTRAINT chk_qa_feedback_rating CHECK (
        rating IN (
            'HELPFUL',
            'WRONG',
            'CITATION_IRRELEVANT',
            'SHOULD_HAVE_ANSWERED',
            'SHOULD_HAVE_REFUSED',
            'TOO_LONG',
            'TOO_SHORT'
        )
    )
);

CREATE INDEX idx_qa_feedback_rating_created ON qa_feedback(rating, created_at DESC);
CREATE INDEX idx_qa_feedback_message_id ON qa_feedback(message_id);
