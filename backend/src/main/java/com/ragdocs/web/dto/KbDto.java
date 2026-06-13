package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record KbDto(
        long id,
        String name,
        String description,
        long documentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
