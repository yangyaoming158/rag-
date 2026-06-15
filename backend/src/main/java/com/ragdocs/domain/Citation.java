package com.ragdocs.domain;

public record Citation(
        long id,
        long messageId,
        Long chunkId,
        int rank,
        double similarity,
        String snippet,
        String documentFilename,
        String headingPath
) {
}
