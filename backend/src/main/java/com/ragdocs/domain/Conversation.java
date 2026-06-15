package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record Conversation(
        long id,
        long userId,
        long kbId,
        String title,
        OffsetDateTime createdAt
) {
}
