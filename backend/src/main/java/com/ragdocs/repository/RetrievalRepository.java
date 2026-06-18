package com.ragdocs.repository;

import com.ragdocs.retrieval.RetrievalHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class RetrievalRepository {
    private static final int RRF_K = 60;
    private static final int MAX_KEYWORD_TERMS = 12;
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}_./:-]+");

    private final JdbcTemplate jdbcTemplate;

    public RetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RetrievalHit> search(long kbId, float[] queryVector, int topK) {
        return search(kbId, queryVector, "", topK);
    }

    public List<RetrievalHit> search(long kbId, float[] queryVector, String query, int topK) {
        int candidateLimit = candidateLimit(topK);
        List<RetrievalHit> vectorHits = vectorSearch(kbId, queryVector, candidateLimit);
        List<RetrievalHit> keywordHits = keywordSearch(kbId, query, candidateLimit);
        return fuse(vectorHits, keywordHits, topK);
    }

    private List<RetrievalHit> vectorSearch(long kbId, float[] queryVector, int topK) {
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

    private List<RetrievalHit> keywordSearch(long kbId, String query, int topK) {
        List<String> terms = keywordTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        String scoreExpression = terms.stream()
                .map(term -> "CASE WHEN search_text LIKE ? ESCAPE '\\' THEN 1.0 ELSE 0.0 END")
                .reduce((left, right) -> left + " + " + right)
                .orElse("0.0");
        String whereExpression = terms.stream()
                .map(term -> "search_text LIKE ? ESCAPE '\\'")
                .reduce((left, right) -> left + " OR " + right)
                .orElse("false");
        String sql = """
                WITH base AS (
                    SELECT c.id AS chunk_id, c.document_id, d.original_filename, c.chunk_index,
                           c.content, c.heading_path, c.page_start, c.page_end, c.char_len,
                           lower(concat_ws(' ', d.original_filename, c.heading_path, c.content)) AS search_text
                    FROM document_chunks c
                    JOIN documents d ON d.id = c.document_id AND d.status = 'READY'
                    WHERE c.kb_id = ?
                )
                SELECT chunk_id, document_id, original_filename, chunk_index, content, heading_path,
                       page_start, page_end, char_len,
                       (%s) / ? AS keyword_score
                FROM base
                WHERE %s
                ORDER BY keyword_score DESC, chunk_id ASC
                LIMIT ?
                """.formatted(scoreExpression, whereExpression);
        List<Object> args = new ArrayList<>();
        args.add(kbId);
        terms.stream().map(this::likePattern).forEach(args::add);
        args.add((double) terms.size());
        terms.stream().map(this::likePattern).forEach(args::add);
        args.add(topK);
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
                0.0,
                rs.getDouble("keyword_score"),
                0.0
        ), args.toArray());
    }

    private List<RetrievalHit> fuse(List<RetrievalHit> vectorHits, List<RetrievalHit> keywordHits, int topK) {
        Map<Long, FusedHit> fused = new LinkedHashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            RetrievalHit hit = vectorHits.get(i);
            FusedHit item = fused.computeIfAbsent(hit.chunkId(), id -> new FusedHit(hit));
            item.vectorRank = i + 1;
            item.vectorScore = hit.similarity();
        }
        for (int i = 0; i < keywordHits.size(); i++) {
            RetrievalHit hit = keywordHits.get(i);
            FusedHit item = fused.computeIfAbsent(hit.chunkId(), id -> new FusedHit(hit));
            item.keywordRank = i + 1;
            item.keywordScore = hit.keywordScore();
        }
        return fused.values().stream()
                .map(FusedHit::toHit)
                .sorted(Comparator.comparingDouble(RetrievalHit::finalScore).reversed()
                        .thenComparing(Comparator.comparingDouble(RetrievalHit::similarity).reversed())
                        .thenComparing(Comparator.comparingDouble(RetrievalHit::keywordScore).reversed())
                        .thenComparingLong(RetrievalHit::chunkId))
                .limit(topK)
                .toList();
    }

    private List<String> keywordTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Matcher matcher = KEYWORD_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find() && terms.size() < MAX_KEYWORD_TERMS) {
            String term = matcher.group().strip();
            if (term.length() >= 2 && !terms.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    private String likePattern(String term) {
        String escaped = term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private int candidateLimit(int topK) {
        return Math.max(topK, Math.min(100, topK * 3));
    }

    private double rrf(Integer rank) {
        return rank == null ? 0.0 : 1.0 / (RRF_K + rank);
    }

    private class FusedHit {
        private final RetrievalHit hit;
        private Integer vectorRank;
        private Integer keywordRank;
        private double vectorScore;
        private double keywordScore;

        private FusedHit(RetrievalHit hit) {
            this.hit = hit;
            this.vectorScore = hit.similarity();
            this.keywordScore = hit.keywordScore();
        }

        private RetrievalHit toHit() {
            double finalScore = rrf(vectorRank) + rrf(keywordRank);
            return new RetrievalHit(
                    hit.chunkId(),
                    hit.documentId(),
                    hit.documentFilename(),
                    hit.chunkIndex(),
                    hit.content(),
                    hit.headingPath(),
                    hit.pageStart(),
                    hit.pageEnd(),
                    hit.charLen(),
                    vectorScore,
                    keywordScore,
                    finalScore
            );
        }
    }
}
