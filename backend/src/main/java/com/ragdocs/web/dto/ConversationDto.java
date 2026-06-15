package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record ConversationDto(
        long id,
        long kbId,
        String title,
        OffsetDateTime createdAt
) {
}
