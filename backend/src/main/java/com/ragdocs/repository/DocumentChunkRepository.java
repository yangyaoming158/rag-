package com.ragdocs.repository;

import com.ragdocs.ingestion.ChunkDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentChunkRepository {
    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteByDocumentId(long documentId) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    public void insertAll(long documentId, long kbId, List<ChunkDraft> chunks) {
        String sql = """
                INSERT INTO document_chunks
                    (document_id, kb_id, chunk_index, content, heading_path, page_start, page_end, char_len)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, chunks, chunks.size(), (ps, chunk) -> {
            ps.setLong(1, documentId);
            ps.setLong(2, kbId);
            ps.setInt(3, chunk.chunkIndex());
            ps.setString(4, chunk.content());
            ps.setString(5, chunk.headingPath());
            if (chunk.pageStart() == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, chunk.pageStart());
            }
            if (chunk.pageEnd() == null) {
                ps.setNull(7, java.sql.Types.INTEGER);
            } else {
                ps.setInt(7, chunk.pageEnd());
            }
            ps.setInt(8, chunk.charLen());
        });
    }

    public long countByDocumentId(long documentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ?",
                Long.class,
                documentId
        );
        return count == null ? 0 : count;
    }
}
