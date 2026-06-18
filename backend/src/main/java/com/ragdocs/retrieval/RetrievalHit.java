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
        double similarity,
        double keywordScore,
        double finalScore
) {
    public RetrievalHit(
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
        this(
                chunkId,
                documentId,
                documentFilename,
                chunkIndex,
                content,
                headingPath,
                pageStart,
                pageEnd,
                charLen,
                similarity,
                0.0,
                similarity
        );
    }
}
