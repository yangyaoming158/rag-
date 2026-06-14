package com.ragdocs.repository;

import com.ragdocs.ingestion.ChunkDraft;
import com.ragdocs.domain.ChunkEmbeddingInput;
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

    public List<ChunkEmbeddingInput> findEmbeddingInputs(long documentId) {
        String sql = """
                SELECT c.id,
                       CONCAT_WS(E'\\n\\n', d.original_filename, c.heading_path, c.content) AS embedding_text
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE c.document_id = ?
                ORDER BY c.chunk_index ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ChunkEmbeddingInput(
                rs.getLong("id"),
                rs.getString("embedding_text")
        ), documentId);
    }

    public void updateEmbeddings(List<ChunkEmbeddingInput> chunks, List<float[]> embeddings, String modelName) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks 与 embeddings 数量不一致");
        }
        String sql = """
                UPDATE document_chunks
                SET embedding = ?::vector, embedding_model = ?
                WHERE id = ?
                """;
        jdbcTemplate.batchUpdate(sql, chunks, chunks.size(), (ps, chunk) -> {
            int index = chunks.indexOf(chunk);
            ps.setString(1, toVectorLiteral(embeddings.get(index)));
            ps.setString(2, modelName);
            ps.setLong(3, chunk.id());
        });
    }

    public long countMissingEmbeddings(long documentId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM document_chunks
                WHERE document_id = ? AND embedding IS NULL
                """, Long.class, documentId);
        return count == null ? 0 : count;
    }

    public static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            float value = vector[i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("向量包含非法数值");
            }
            builder.append(Float.toString(value));
        }
        return builder.append(']').toString();
    }
}
