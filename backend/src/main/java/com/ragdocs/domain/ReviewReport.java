package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record ReviewReport(
        long id,
        long userId,
        long kbId,
        String reviewType,
        String supplement,
        String status,
        String conclusion,
        String riskLevel,
        String issues,
        String suggestions,
        String citationWarning,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        OffsetDateTime createdAt
) {
}
