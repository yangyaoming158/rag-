package com.ragdocs.repository;

import com.ragdocs.domain.QaFeedback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QaFeedbackRepository {
    private final JdbcTemplate jdbcTemplate;

    public QaFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QaFeedback upsert(long messageId, long userId, String rating, String reason, String comment) {
        String sql = """
                INSERT INTO qa_feedback (message_id, user_id, rating, reason, comment)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (message_id, user_id) DO UPDATE SET
                    rating = EXCLUDED.rating,
                    reason = EXCLUDED.reason,
                    comment = EXCLUDED.comment,
                    created_at = now()
                RETURNING id, message_id, user_id, rating, reason, comment, created_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapFeedback,
                messageId, userId, rating, truncate(reason, 120), truncate(comment, 1000));
    }

    public Map<Long, String> findRatingsByUserAndMessageIds(long userId, List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", messageIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT message_id, rating
                FROM qa_feedback
                WHERE user_id = ? AND message_id IN (%s)
                """.formatted(placeholders);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(messageIds);
        Map<Long, String> ratings = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            ratings.put(rs.getLong("message_id"), rs.getString("rating"));
        }, args.toArray());
        return ratings;
    }

    public List<AdminQaFeedbackRow> findAdminLowQualityFeedback(String rating, int page, int size) {
        int offset = page * size;
        StringBuilder sql = new StringBuilder("""
                SELECT f.id, f.message_id, f.user_id, u.username, f.rating, f.reason, f.comment, f.created_at,
                       m.conversation_id, m.content AS answer, m.status AS answer_status,
                       m.prompt_tokens, m.completion_tokens, m.latency_ms AS answer_latency_ms,
                       c.kb_id, kb.name AS kb_name,
                       q.id AS question_message_id, q.content AS question,
                       l.provider, l.model, l.latency_ms AS model_latency_ms
                FROM qa_feedback f
                JOIN messages m ON m.id = f.message_id
                JOIN conversations c ON c.id = m.conversation_id
                JOIN knowledge_bases kb ON kb.id = c.kb_id
                JOIN users u ON u.id = f.user_id
                LEFT JOIN LATERAL (
                    SELECT qm.id, qm.content
                    FROM messages qm
                    WHERE qm.conversation_id = m.conversation_id
                      AND qm.role = 'USER'
                      AND (qm.created_at, qm.id) < (m.created_at, m.id)
                    ORDER BY qm.created_at DESC, qm.id DESC
                    LIMIT 1
                ) q ON true
                LEFT JOIN LATERAL (
                    SELECT provider, model, latency_ms
                    FROM model_call_logs
                    WHERE message_id = m.id AND call_type = 'CHAT'
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                ) l ON true
                WHERE f.rating <> 'HELPFUL'
                """);
        List<Object> args = new ArrayList<>();
        if (rating != null) {
            sql.append(" AND f.rating = ?");
            args.add(rating);
        }
        sql.append(" ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), this::mapAdminRow, args.toArray());
    }

    public long countAdminLowQualityFeedback(String rating) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM qa_feedback f WHERE f.rating <> 'HELPFUL'");
        List<Object> args = new ArrayList<>();
        if (rating != null) {
            sql.append(" AND f.rating = ?");
            args.add(rating);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private QaFeedback mapFeedback(ResultSet rs, int rowNum) throws SQLException {
        return new QaFeedback(
                rs.getLong("id"),
                rs.getLong("message_id"),
                rs.getLong("user_id"),
                rs.getString("rating"),
                rs.getString("reason"),
                rs.getString("comment"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private AdminQaFeedbackRow mapAdminRow(ResultSet rs, int rowNum) throws SQLException {
        return new AdminQaFeedbackRow(
                rs.getLong("id"),
                rs.getLong("message_id"),
                rs.getLong("conversation_id"),
                rs.getLong("kb_id"),
                rs.getString("kb_name"),
                rs.getLong("user_id"),
                rs.getString("username"),
                nullableLong(rs, "question_message_id"),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getString("answer_status"),
                nullableInteger(rs, "prompt_tokens"),
                nullableInteger(rs, "completion_tokens"),
                nullableLong(rs, "answer_latency_ms"),
                rs.getString("provider"),
                rs.getString("model"),
                nullableLong(rs, "model_latency_ms"),
                rs.getString("rating"),
                rs.getString("reason"),
                rs.getString("comment"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record AdminQaFeedbackRow(
            long id,
            long messageId,
            long conversationId,
            long kbId,
            String kbName,
            long userId,
            String username,
            Long questionMessageId,
            String question,
            String answer,
            String answerStatus,
            Integer promptTokens,
            Integer completionTokens,
            Long answerLatencyMs,
            String provider,
            String model,
            Long modelLatencyMs,
            String rating,
            String reason,
            String comment,
            OffsetDateTime createdAt
    ) {
    }
}
