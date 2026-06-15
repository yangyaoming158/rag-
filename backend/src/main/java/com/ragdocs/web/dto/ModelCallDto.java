package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record ModelCallDto(
        long id,
        String callType,
        String provider,
        String model,
        Long messageId,
        Long documentId,
        String documentFilename,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        String status,
        String errorMessage,
        OffsetDateTime createdAt
) {
}
