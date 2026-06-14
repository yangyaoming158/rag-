package com.ragdocs.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModelCallLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public ModelCallLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordEmbedding(
            Long documentId,
            String provider,
            String model,
            int promptTokens,
            long latencyMs,
            String status,
            String errorMessage
    ) {
        jdbcTemplate.update("""
                INSERT INTO model_call_logs
                    (call_type, provider, model, document_id, prompt_tokens, completion_tokens, latency_ms, status, error_message)
                VALUES ('EMBEDDING', ?, ?, ?, ?, NULL, ?, ?, ?)
                """, provider, model, documentId, promptTokens, latencyMs, status, truncate(errorMessage));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
