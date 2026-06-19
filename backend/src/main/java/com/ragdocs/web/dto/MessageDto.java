package com.ragdocs.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageDto(
        long id,
        String role,
        String content,
        String status,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        OffsetDateTime createdAt,
        List<CitationDto> citations,
        String feedbackRating
) {
}
