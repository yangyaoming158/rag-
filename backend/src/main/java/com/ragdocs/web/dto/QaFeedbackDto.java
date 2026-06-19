package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record QaFeedbackDto(
        long id,
        long messageId,
        String rating,
        String reason,
        String comment,
        OffsetDateTime createdAt
) {
}
