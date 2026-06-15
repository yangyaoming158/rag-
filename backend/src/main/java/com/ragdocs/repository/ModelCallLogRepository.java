package com.ragdocs.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

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

    public void recordChat(
            Long messageId,
            String provider,
            String model,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            String status,
            String errorMessage
    ) {
        jdbcTemplate.update("""
                INSERT INTO model_call_logs
                    (call_type, provider, model, message_id, prompt_tokens, completion_tokens, latency_ms, status, error_message)
                VALUES ('CHAT', ?, ?, ?, ?, ?, ?, ?, ?)
                """, provider, model, messageId, promptTokens, completionTokens, latencyMs, status, truncate(errorMessage));
    }

    public List<ModelCallLogRow> findAdminCalls(String type, String status, int page, int size) {
        int offset = page * size;
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.call_type, l.provider, l.model, l.message_id, l.document_id,
                       d.original_filename AS document_filename,
                       l.prompt_tokens, l.completion_tokens, l.latency_ms, l.status,
                       l.error_message, l.created_at
                FROM model_call_logs l
                LEFT JOIN documents d ON d.id = l.document_id
                WHERE 1 = 1
                """);
        new FilterBuilder(sql).append(type, status);
        sql.append(" ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?");
        Object[] args = buildArgs(type, status, size, offset);
        return jdbcTemplate.query(sql.toString(), this::mapCall, args);
    }

    public long countAdminCalls(String type, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM model_call_logs l WHERE 1 = 1");
        new FilterBuilder(sql).append(type, status);
        Object[] args = buildArgs(type, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args);
        return count == null ? 0 : count;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Object[] buildArgs(String type, String status, Object... tail) {
        int count = 0;
        if (type != null) {
            count++;
        }
        if (status != null) {
            count++;
        }
        Object[] args = new Object[count + tail.length];
        int index = 0;
        if (type != null) {
            args[index++] = type;
        }
        if (status != null) {
            args[index++] = status;
        }
        for (Object value : tail) {
            args[index++] = value;
        }
        return args;
    }

    private ModelCallLogRow mapCall(ResultSet rs, int rowNum) throws SQLException {
        return new ModelCallLogRow(
                rs.getLong("id"),
                rs.getString("call_type"),
                rs.getString("provider"),
                rs.getString("model"),
                nullableLong(rs, "message_id"),
                nullableLong(rs, "document_id"),
                rs.getString("document_filename"),
                nullableInteger(rs, "prompt_tokens"),
                nullableInteger(rs, "completion_tokens"),
                nullableLong(rs, "latency_ms"),
                rs.getString("status"),
                rs.getString("error_message"),
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

    private static class FilterBuilder {
        private final StringBuilder sql;

        FilterBuilder(StringBuilder sql) {
            this.sql = sql;
        }

        void append(String type, String status) {
            if (type != null) {
                sql.append(" AND l.call_type = ?");
            }
            if (status != null) {
                sql.append(" AND l.status = ?");
            }
        }
    }

    public record ModelCallLogRow(
            long id,
            String callType,
            String provider,
            String model,
            Long messageId,
            Long documentId,
            String documentFilename,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs,
            String status,
            String errorMessage,
            OffsetDateTime createdAt
    ) {
    }
}
