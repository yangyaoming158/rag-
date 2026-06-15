package com.ragdocs.rag;

public record CitationDraft(
        int rank,
        long chunkId,
        String documentFilename,
        String headingPath,
        String snippet,
        double similarity
) {
}
