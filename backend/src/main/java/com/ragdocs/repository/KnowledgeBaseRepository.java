package com.ragdocs.repository;

import com.ragdocs.domain.KnowledgeBase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeBaseRepository {
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KnowledgeBase create(long ownerId, String name, String description) {
        String sql = """
                INSERT INTO knowledge_bases (owner_id, name, description)
                VALUES (?, ?, ?)
                RETURNING id, owner_id, name, description, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapKnowledgeBase, ownerId, name, description);
    }

    public List<KbRow> listByOwner(long ownerId) {
        String sql = """
                SELECT kb.id, kb.owner_id, kb.name, kb.description, kb.created_at, kb.updated_at,
                       COUNT(d.id) AS document_count
                FROM knowledge_bases kb
                LEFT JOIN documents d ON d.kb_id = kb.id
                WHERE kb.owner_id = ?
                GROUP BY kb.id
                ORDER BY kb.created_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new KbRow(mapKnowledgeBase(rs, rowNum), rs.getLong("document_count")), ownerId);
    }

    public Optional<KnowledgeBase> findByIdAndOwner(long id, long ownerId) {
        String sql = """
                SELECT id, owner_id, name, description, created_at, updated_at
                FROM knowledge_bases
                WHERE id = ? AND owner_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapKnowledgeBase, id, ownerId).stream().findFirst();
    }

    public int deleteByIdAndOwner(long id, long ownerId) {
        return jdbcTemplate.update("DELETE FROM knowledge_bases WHERE id = ? AND owner_id = ?", id, ownerId);
    }

    public record KbRow(KnowledgeBase knowledgeBase, long documentCount) {
    }

    private KnowledgeBase mapKnowledgeBase(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeBase(
                rs.getLong("id"),
                rs.getLong("owner_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
