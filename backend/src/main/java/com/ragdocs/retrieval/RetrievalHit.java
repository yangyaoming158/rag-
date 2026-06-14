package com.ragdocs.retrieval;

public record RetrievalHit(
        long chunkId,
        long documentId,
        String documentFilename,
        int chunkIndex,
        String content,
        String headingPath,
        Integer pageStart,
        Integer pageEnd,
        int charLen,
        double similarity
) {
}
