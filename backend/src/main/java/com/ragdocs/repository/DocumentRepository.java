package com.ragdocs.repository;

import com.ragdocs.domain.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Document createUploaded(long kbId, String originalFilename, String contentType, long fileSize, String sha256) {
        String sql = """
                INSERT INTO documents (kb_id, original_filename, content_type, file_size, storage_path, sha256, status)
                VALUES (?, ?, ?, ?, '', ?, 'UPLOADED')
                RETURNING id, kb_id, original_filename, content_type, file_size, storage_path, sha256,
                          status, error_message, chunk_count, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapDocument, kbId, originalFilename, contentType, fileSize, sha256);
    }

    public Optional<Document> findByKbIdAndSha256(long kbId, String sha256) {
        String sql = """
                SELECT id, kb_id, original_filename, content_type, file_size, storage_path, sha256,
                       status, error_message, chunk_count, created_at, updated_at
                FROM documents
                WHERE kb_id = ? AND sha256 = ?
                """;
        return jdbcTemplate.query(sql, this::mapDocument, kbId, sha256).stream().findFirst();
    }

    public Optional<Document> findByIdAndOwner(long id, long ownerId) {
        String sql = """
                SELECT d.id, d.kb_id, d.original_filename, d.content_type, d.file_size, d.storage_path,
                       d.sha256, d.status, d.error_message, d.chunk_count, d.created_at, d.updated_at
                FROM documents d
                JOIN knowledge_bases kb ON kb.id = d.kb_id
                WHERE d.id = ? AND kb.owner_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapDocument, id, ownerId).stream().findFirst();
    }

    public List<Document> listByKb(long kbId, String status, int page, int size) {
        int offset = page * size;
        if (status == null || status.isBlank()) {
            String sql = """
                    SELECT id, kb_id, original_filename, content_type, file_size, storage_path, sha256,
                           status, error_message, chunk_count, created_at, updated_at
                    FROM documents
                    WHERE kb_id = ?
                    ORDER BY created_at DESC
                    LIMIT ? OFFSET ?
                    """;
            return jdbcTemplate.query(sql, this::mapDocument, kbId, size, offset);
        }
        String sql = """
                SELECT id, kb_id, original_filename, content_type, file_size, storage_path, sha256,
                       status, error_message, chunk_count, created_at, updated_at
                FROM documents
                WHERE kb_id = ? AND status = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, this::mapDocument, kbId, status, size, offset);
    }

    public long countByKb(long kbId, String status) {
        if (status == null || status.isBlank()) {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents WHERE kb_id = ?", Long.class, kbId);
            return count == null ? 0 : count;
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents WHERE kb_id = ? AND status = ?", Long.class, kbId, status);
        return count == null ? 0 : count;
    }

    public void updateStoragePath(long id, String storagePath) {
        jdbcTemplate.update("UPDATE documents SET storage_path = ?, updated_at = now() WHERE id = ?", storagePath, id);
    }

    public int deleteByIdAndOwner(long id, long ownerId) {
        String sql = """
                DELETE FROM documents d
                USING knowledge_bases kb
                WHERE d.kb_id = kb.id AND d.id = ? AND kb.owner_id = ?
                """;
        return jdbcTemplate.update(sql, id, ownerId);
    }

    public List<String> findStoragePathsByKbIdAndOwner(long kbId, long ownerId) {
        String sql = """
                SELECT d.storage_path
                FROM documents d
                JOIN knowledge_bases kb ON kb.id = d.kb_id
                WHERE d.kb_id = ? AND kb.owner_id = ? AND d.storage_path <> ''
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("storage_path"), kbId, ownerId);
    }

    private Document mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new Document(
                rs.getLong("id"),
                rs.getLong("kb_id"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("file_size"),
                rs.getString("storage_path"),
                rs.getString("sha256"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getInt("chunk_count"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
