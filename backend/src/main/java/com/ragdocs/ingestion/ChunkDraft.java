package com.ragdocs.ingestion;

public record ChunkDraft(
        String content,
        int chunkIndex,
        String headingPath,
        Integer pageStart,
        Integer pageEnd,
        int charLen
) {
}
