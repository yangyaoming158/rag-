package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record RagMessage(
        long id,
        long conversationId,
        String role,
        String content,
        String status,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        OffsetDateTime createdAt
) {
}
