package com.ragdocs.repository;

import com.ragdocs.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByUsername(String username) {
        String sql = """
                SELECT id, username, password_hash, role, created_at
                FROM users
                WHERE username = ?
                """;
        return jdbcTemplate.query(sql, this::mapUser, username).stream().findFirst();
    }

    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
