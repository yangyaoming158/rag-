package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record KnowledgeBase(
        long id,
        long ownerId,
        String name,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
