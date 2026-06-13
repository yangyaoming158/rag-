package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record JobDto(
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
