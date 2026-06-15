package com.ragdocs.web.dto;

import java.util.List;

public record RagAnswerDto(
        long userMessageId,
        long assistantMessageId,
        String answer,
        String status,
        List<CitationDto> citations,
        String citationWarning,
        long latencyMs
) {
}
