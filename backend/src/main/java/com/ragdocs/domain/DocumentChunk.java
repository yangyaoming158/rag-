package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record DocumentChunk(
        long id,
        long documentId,
        long kbId,
        int chunkIndex,
        String content,
        String headingPath,
        Integer pageStart,
        Integer pageEnd,
        int charLen,
        String embeddingModel,
        OffsetDateTime createdAt
) {
}
