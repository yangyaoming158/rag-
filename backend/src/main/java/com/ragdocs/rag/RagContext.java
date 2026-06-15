package com.ragdocs.rag;

public record RagContext(
        int number,
        long chunkId,
        long documentId,
        String documentFilename,
        String headingPath,
        Integer pageStart,
        Integer pageEnd,
        String content,
        double similarity
) {
}
