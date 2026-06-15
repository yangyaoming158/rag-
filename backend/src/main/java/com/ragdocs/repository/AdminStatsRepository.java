package com.ragdocs.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminStatsRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminStatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OverviewStats overview() {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM knowledge_bases) AS kb_count,
                    (SELECT COUNT(*) FROM documents) AS doc_count,
                    (SELECT COUNT(*) FROM document_chunks) AS chunk_count,
                    (SELECT COALESCE(SUM(COALESCE(prompt_tokens, 0) + COALESCE(completion_tokens, 0)), 0)
                     FROM model_call_logs) AS token_sum,
                    (SELECT COALESCE(ROUND(AVG(latency_ms)), 0)
                     FROM model_call_logs
                     WHERE latency_ms IS NOT NULL) AS avg_latency_ms
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new OverviewStats(
                rs.getLong("kb_count"),
                rs.getLong("doc_count"),
                rs.getLong("chunk_count"),
                rs.getLong("token_sum"),
                rs.getLong("avg_latency_ms")
        ));
    }

    public record OverviewStats(
            long kbCount,
            long docCount,
            long chunkCount,
            long tokenSum,
            long avgLatencyMs
    ) {
    }
}
