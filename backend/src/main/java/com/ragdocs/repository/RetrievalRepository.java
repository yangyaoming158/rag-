package com.ragdocs.repository;

import com.ragdocs.retrieval.RetrievalHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RetrievalRepository {
    private final JdbcTemplate jdbcTemplate;

    public RetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievalHit> search(long kbId, float[] queryVector, int topK) {
        String vectorLiteral = DocumentChunkRepository.toVectorLiteral(queryVector);
        String sql = """
                SELECT c.id AS chunk_id, c.document_id, d.original_filename, c.chunk_index,
                       c.content, c.heading_path, c.page_start, c.page_end, c.char_len,
                       1 - (c.embedding <=> ?::vector) AS similarity
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id AND d.status = 'READY'
                WHERE c.kb_id = ? AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RetrievalHit(
                rs.getLong("chunk_id"),
                rs.getLong("document_id"),
                rs.getString("original_filename"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("heading_path"),
                rs.getObject("page_start", Integer.class),
                rs.getObject("page_end", Integer.class),
                rs.getInt("char_len"),
                rs.getDouble("similarity")
        ), vectorLiteral, kbId, vectorLiteral, topK);
    }
}
