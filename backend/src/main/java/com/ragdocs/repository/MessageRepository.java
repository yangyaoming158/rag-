package com.ragdocs.repository;

import com.ragdocs.domain.RagMessage;
import com.ragdocs.rag.RagHistoryMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class MessageRepository {
    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RagMessage create(
            long conversationId,
            String role,
            String content,
            String status,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs
    ) {
        String sql = """
                INSERT INTO messages
                    (conversation_id, role, content, status, prompt_tokens, completion_tokens, latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id, conversation_id, role, content, status, prompt_tokens, completion_tokens, latency_ms, created_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapMessage,
                conversationId, role, content, status, promptTokens, completionTokens, latencyMs);
    }

    public List<RagMessage> findByConversationId(long conversationId) {
        String sql = """
                SELECT id, conversation_id, role, content, status, prompt_tokens, completion_tokens, latency_ms, created_at
                FROM messages
                WHERE conversation_id = ?
                ORDER BY created_at ASC, id ASC
                """;
        return jdbcTemplate.query(sql, this::mapMessage, conversationId);
    }

    public List<RagHistoryMessage> findRecentHistory(long conversationId, int limit) {
        String sql = """
                SELECT role, content
                FROM (
                    SELECT role, content, created_at, id
                    FROM messages
                    WHERE conversation_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                ) recent
                ORDER BY created_at ASC, id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RagHistoryMessage(
                rs.getString("role"),
                rs.getString("content")
        ), conversationId, limit);
    }

    private RagMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        Integer promptTokens = (Integer) rs.getObject("prompt_tokens");
        Integer completionTokens = (Integer) rs.getObject("completion_tokens");
        Long latencyMs = (Long) rs.getObject("latency_ms");
        return new RagMessage(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("status"),
                promptTokens,
                completionTokens,
                latencyMs,
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
