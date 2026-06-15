package com.ragdocs.web.dto;

public record CitationDto(
        int rank,
        Long chunkId,
        String documentFilename,
        String headingPath,
        String snippet,
        double similarity
) {
}
