package com.ragdocs.repository;

import com.ragdocs.domain.IngestionJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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

    public List<AdminIngestionJobRow> findAdminJobs(String status, int page, int size) {
        int offset = page * size;
        if (status == null || status.isBlank()) {
            String sql = """
                    SELECT j.id, j.document_id, d.original_filename AS document_filename,
                           d.status AS document_status, kb.id AS kb_id, kb.name AS kb_name,
                           j.phase, j.status, j.attempt, j.max_attempt, j.error_message,
                           j.started_at, j.finished_at, j.created_at
                    FROM ingestion_jobs j
                    JOIN documents d ON d.id = j.document_id
                    JOIN knowledge_bases kb ON kb.id = d.kb_id
                    ORDER BY j.created_at DESC, j.id DESC
                    LIMIT ? OFFSET ?
                    """;
            return jdbcTemplate.query(sql, this::mapAdminRow, size, offset);
        }
        String sql = """
                SELECT j.id, j.document_id, d.original_filename AS document_filename,
                       d.status AS document_status, kb.id AS kb_id, kb.name AS kb_name,
                       j.phase, j.status, j.attempt, j.max_attempt, j.error_message,
                       j.started_at, j.finished_at, j.created_at
                FROM ingestion_jobs j
                JOIN documents d ON d.id = j.document_id
                JOIN knowledge_bases kb ON kb.id = d.kb_id
                WHERE j.status = ?
                ORDER BY j.created_at DESC, j.id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, this::mapAdminRow, status, size, offset);
    }

    public long countAdminJobs(String status) {
        if (status == null || status.isBlank()) {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ingestion_jobs", Long.class);
            return count == null ? 0 : count;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ingestion_jobs WHERE status = ?",
                Long.class,
                status
        );
        return count == null ? 0 : count;
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

    private AdminIngestionJobRow mapAdminRow(ResultSet rs, int rowNum) throws SQLException {
        return new AdminIngestionJobRow(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getString("document_filename"),
                rs.getString("document_status"),
                rs.getLong("kb_id"),
                rs.getString("kb_name"),
                rs.getString("phase"),
                rs.getString("status"),
                rs.getInt("attempt"),
                rs.getInt("max_attempt"),
                rs.getString("error_message"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    public record AdminIngestionJobRow(
            long id,
            long documentId,
            String documentFilename,
            String documentStatus,
            long kbId,
            String kbName,
            String phase,
            String status,
            int attempt,
            int maxAttempt,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime createdAt
    ) {
    }
}
