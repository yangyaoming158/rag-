package com.ragdocs.domain;

public record ReviewCitation(
        long id,
        long reviewId,
        Long chunkId,
        int rank,
        double similarity,
        String snippet,
        String documentFilename,
        String headingPath
) {
}
