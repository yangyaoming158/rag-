package com.ragdocs.repository;

import com.ragdocs.domain.IngestionJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class IngestionJobRepository {
    private final JdbcTemplate jdbcTemplate;

    public IngestionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionJob createParseJob(long documentId) {
        String sql = """
                INSERT INTO ingestion_jobs (document_id, phase, status)
                VALUES (?, 'PARSE', 'PENDING')
                RETURNING id, document_id, phase, status, attempt, max_attempt, error_message,
                          started_at, finished_at, created_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapJob, documentId);
    }

    public IngestionJob createJob(long documentId, String phase) {
        String sql = """
                INSERT INTO ingestion_jobs (document_id, phase, status)
                VALUES (?, ?, 'PENDING')
                RETURNING id, document_id, phase, status, attempt, max_attempt, error_message,
                          started_at, finished_at, created_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapJob, documentId, phase);
    }

    public void markRunning(long id) {
        jdbcTemplate.update("""
                UPDATE ingestion_jobs
                SET status = 'RUNNING', attempt = attempt + 1, started_at = now(), error_message = NULL
                WHERE id = ?
                """, id);
    }

    public void markSucceeded(long id) {
        jdbcTemplate.update("""
                UPDATE ingestion_jobs
                SET status = 'SUCCEEDED', finished_at = now(), error_message = NULL
                WHERE id = ?
                """, id);
    }

    public void markFailed(long id, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE ingestion_jobs
                SET status = 'FAILED', finished_at = now(), error_message = ?
                WHERE id = ?
                """, truncate(errorMessage), id);
    }

    public List<IngestionJob> findByDocumentId(long documentId) {
        String sql = """
                SELECT id, document_id, phase, status, attempt, max_attempt, error_message,
                       started_at, finished_at, created_at
                FROM ingestion_jobs
                WHERE document_id = ?
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, this::mapJob, documentId);
    }

    public Optional<IngestionJob> findLatestPending(long documentId, String phase) {
        String sql = """
                SELECT id, document_id, phase, status, attempt, max_attempt, error_message,
                       started_at, finished_at, created_at
                FROM ingestion_jobs
                WHERE document_id = ? AND phase = ? AND status = 'PENDING'
                ORDER BY created_at DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapJob, documentId, phase).stream().findFirst();
    }

    public boolean hasRunningJob(long documentId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM ingestion_jobs
                    WHERE document_id = ? AND status = 'RUNNING'
                )
                """, Boolean.class, documentId);
        return Boolean.TRUE.equals(exists);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private IngestionJob mapJob(ResultSet rs, int rowNum) throws SQLException {
        return new IngestionJob(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getString("phase"),
                rs.getString("status"),
                rs.getInt("attempt"),
                rs.getInt("max_attempt"),
                rs.getString("error_message"),
                rs.getObject("started_at", java.time.OffsetDateTime.class),
                rs.getObject("finished_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
