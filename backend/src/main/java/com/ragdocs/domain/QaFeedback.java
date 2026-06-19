package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record QaFeedback(
        long id,
        long messageId,
        long userId,
        String rating,
        String reason,
        String comment,
        OffsetDateTime createdAt
) {
}
