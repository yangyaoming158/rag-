package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record AdminIngestionJobDto(
        long id,
        long documentId,
        String documentFilename,
        String documentStatus,
        long kbId,
        String kbName,
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
