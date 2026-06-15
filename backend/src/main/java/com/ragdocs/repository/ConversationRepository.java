package com.ragdocs.repository;

import com.ragdocs.domain.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Conversation create(long userId, long kbId, String title) {
        String sql = """
                INSERT INTO conversations (user_id, kb_id, title)
                VALUES (?, ?, ?)
                RETURNING id, user_id, kb_id, title, created_at
                """;
        return jdbcTemplate.queryForObject(sql, this::mapConversation, userId, kbId, title);
    }

    public Optional<Conversation> findByIdAndOwner(long id, long ownerId) {
        String sql = """
                SELECT c.id, c.user_id, c.kb_id, c.title, c.created_at
                FROM conversations c
                JOIN knowledge_bases kb ON kb.id = c.kb_id
                WHERE c.id = ? AND c.user_id = ? AND kb.owner_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapConversation, id, ownerId, ownerId).stream().findFirst();
    }

    public List<Conversation> listByOwner(long ownerId, Long kbId) {
        if (kbId == null) {
            String sql = """
                    SELECT c.id, c.user_id, c.kb_id, c.title, c.created_at
                    FROM conversations c
                    JOIN knowledge_bases kb ON kb.id = c.kb_id
                    WHERE c.user_id = ? AND kb.owner_id = ?
                    ORDER BY c.created_at DESC
                    """;
            return jdbcTemplate.query(sql, this::mapConversation, ownerId, ownerId);
        }
        String sql = """
                SELECT c.id, c.user_id, c.kb_id, c.title, c.created_at
                FROM conversations c
                JOIN knowledge_bases kb ON kb.id = c.kb_id
                WHERE c.user_id = ? AND kb.owner_id = ? AND c.kb_id = ?
                ORDER BY c.created_at DESC
                """;
        return jdbcTemplate.query(sql, this::mapConversation, ownerId, ownerId, kbId);
    }

    public void updateTitle(long id, String title) {
        jdbcTemplate.update("UPDATE conversations SET title = ? WHERE id = ?", title, id);
    }

    private Conversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("kb_id"),
                rs.getString("title"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
