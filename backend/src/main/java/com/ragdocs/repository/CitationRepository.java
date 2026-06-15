package com.ragdocs.repository;

import com.ragdocs.domain.Citation;
import com.ragdocs.rag.CitationDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CitationRepository {
    private final JdbcTemplate jdbcTemplate;

    public CitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(long messageId, List<CitationDraft> citations) {
        if (citations.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO citations
                    (message_id, chunk_id, rank, similarity, snippet, document_filename)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, citations, citations.size(), (ps, citation) -> {
            ps.setLong(1, messageId);
            ps.setLong(2, citation.chunkId());
            ps.setInt(3, citation.rank());
            ps.setDouble(4, citation.similarity());
            ps.setString(5, citation.snippet());
            ps.setString(6, citation.documentFilename());
        });
    }

    public List<Citation> findByMessageIds(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", messageIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT ci.id, ci.message_id, ci.chunk_id, ci.rank, ci.similarity, ci.snippet,
                       ci.document_filename, c.heading_path
                FROM citations ci
                LEFT JOIN document_chunks c ON c.id = ci.chunk_id
                WHERE ci.message_id IN (%s)
                ORDER BY ci.message_id ASC, ci.rank ASC
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long chunkId = rs.getObject("chunk_id", Long.class);
            return new Citation(
                    rs.getLong("id"),
                    rs.getLong("message_id"),
                    chunkId,
                    rs.getInt("rank"),
                    rs.getDouble("similarity"),
                    rs.getString("snippet"),
                    rs.getString("document_filename"),
                    rs.getString("heading_path")
            );
        }, new ArrayList<>(messageIds).toArray());
    }
}
