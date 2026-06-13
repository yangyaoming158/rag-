package com.ragdocs.repository;

import com.ragdocs.domain.IngestionJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
