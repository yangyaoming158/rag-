package com.ragdocs.repository;

import com.ragdocs.domain.ReviewCitation;
import com.ragdocs.domain.ReviewReport;
import com.ragdocs.rag.CitationDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReviewRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReviewReport create(
            long userId,
            long kbId,
            String reviewType,
            String supplement,
            String status,
            String conclusion,
            String riskLevel,
            String issues,
            String suggestions,
            String citationWarning,
            Integer promptTokens,
            Integer completionTokens,
            Long latencyMs
    ) {
        String sql = """
                INSERT INTO review_reports
                    (user_id, kb_id, review_type, supplement, status, conclusion, risk_level,
                     issues, suggestions, citation_warning, prompt_tokens, completion_tokens, latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, user_id, kb_id, review_type, supplement, status, conclusion, risk_level,
                          issues, suggestions, citation_warning, prompt_tokens, completion_tokens, latency_ms, created_at
                """;
        return jdbcTemplate.queryForObject(
                sql,
                this::mapReport,
                userId,
                kbId,
                reviewType,
                supplement,
                status,
                conclusion,
                riskLevel,
                issues,
                suggestions,
                truncate(citationWarning, 500),
                promptTokens,
                completionTokens,
                latencyMs
        );
    }

    public List<ReviewRow> listByOwner(long ownerId, Long kbId) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.user_id, r.kb_id, r.review_type, r.supplement, r.status,
                       r.conclusion, r.risk_level, r.issues, r.suggestions, r.citation_warning,
                       r.prompt_tokens, r.completion_tokens, r.latency_ms, r.created_at,
                       kb.name AS kb_name
                FROM review_reports r
                JOIN knowledge_bases kb ON kb.id = r.kb_id
                WHERE r.user_id = ? AND kb.owner_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        args.add(ownerId);
        if (kbId != null) {
            sql.append(" AND r.kb_id = ?");
            args.add(kbId);
        }
        sql.append(" ORDER BY r.created_at DESC, r.id DESC LIMIT 50");
        return jdbcTemplate.query(sql.toString(), this::mapRow, args.toArray());
    }

    public Optional<ReviewRow> findByIdAndOwner(long id, long ownerId) {
        String sql = """
                SELECT r.id, r.user_id, r.kb_id, r.review_type, r.supplement, r.status,
                       r.conclusion, r.risk_level, r.issues, r.suggestions, r.citation_warning,
                       r.prompt_tokens, r.completion_tokens, r.latency_ms, r.created_at,
                       kb.name AS kb_name
                FROM review_reports r
                JOIN knowledge_bases kb ON kb.id = r.kb_id
                WHERE r.id = ? AND r.user_id = ? AND kb.owner_id = ?
                """;
        return jdbcTemplate.query(sql, this::mapRow, id, ownerId, ownerId).stream().findFirst();
    }

    public void insertCitations(long reviewId, List<CitationDraft> citations) {
        if (citations.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO review_citations
                    (review_id, chunk_id, rank, similarity, snippet, document_filename, heading_path)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, citations, citations.size(), (ps, citation) -> {
            ps.setLong(1, reviewId);
            ps.setLong(2, citation.chunkId());
            ps.setInt(3, citation.rank());
            ps.setDouble(4, citation.similarity());
            ps.setString(5, citation.snippet());
            ps.setString(6, citation.documentFilename());
            ps.setString(7, citation.headingPath());
        });
    }

    public List<ReviewCitation> findCitationsByReviewIds(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", reviewIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT id, review_id, chunk_id, rank, similarity, snippet, document_filename, heading_path
                FROM review_citations
                WHERE review_id IN (%s)
                ORDER BY review_id ASC, rank ASC
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, this::mapCitation, new ArrayList<>(reviewIds).toArray());
    }

    private ReviewRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewRow(mapReport(rs, rowNum), rs.getString("kb_name"));
    }

    private ReviewReport mapReport(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewReport(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("kb_id"),
                rs.getString("review_type"),
                rs.getString("supplement"),
                rs.getString("status"),
                rs.getString("conclusion"),
                rs.getString("risk_level"),
                rs.getString("issues"),
                rs.getString("suggestions"),
                rs.getString("citation_warning"),
                rs.getObject("prompt_tokens", Integer.class),
                rs.getObject("completion_tokens", Integer.class),
                rs.getObject("latency_ms", Long.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private ReviewCitation mapCitation(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewCitation(
                rs.getLong("id"),
                rs.getLong("review_id"),
                rs.getObject("chunk_id", Long.class),
                rs.getInt("rank"),
                rs.getDouble("similarity"),
                rs.getString("snippet"),
                rs.getString("document_filename"),
                rs.getString("heading_path")
        );
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    public record ReviewRow(ReviewReport report, String kbName) {
    }
}
