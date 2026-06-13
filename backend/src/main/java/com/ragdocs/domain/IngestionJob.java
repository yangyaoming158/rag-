package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record IngestionJob(
        long id,
        long documentId,
        String phase,
        String status,
        int attempt,
        int maxAttempt,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt
) {
}
