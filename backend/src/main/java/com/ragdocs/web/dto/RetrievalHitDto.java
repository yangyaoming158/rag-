package com.ragdocs.web.dto;

public record RetrievalHitDto(
        int rank,
        long chunkId,
        long documentId,
        String documentFilename,
        int chunkIndex,
        String headingPath,
        Integer pageStart,
        Integer pageEnd,
        int charLen,
        double similarity,
        double keywordScore,
        double finalScore,
        boolean aboveThreshold,
        String contentPreview
) {
}
