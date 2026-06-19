package com.ragdocs.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminQaFeedbackDto(
        long id,
        long messageId,
        long conversationId,
        long kbId,
        String kbName,
        long userId,
        String username,
        Long questionMessageId,
        String question,
        String answer,
        String answerStatus,
        Integer promptTokens,
        Integer completionTokens,
        Long answerLatencyMs,
        String provider,
        String model,
        Long modelLatencyMs,
        String rating,
        String reason,
        String comment,
        OffsetDateTime createdAt,
        List<CitationDto> citations
) {
}
