package com.ragdocs.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReviewDto(
        long id,
        long kbId,
        String kbName,
        String reviewType,
        String reviewTypeLabel,
        String supplement,
        String status,
        String conclusion,
        String riskLevel,
        String issues,
        String suggestions,
        List<CitationDto> citations,
        String citationWarning,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        OffsetDateTime createdAt
) {
}
