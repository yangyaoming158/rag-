package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record User(
        Long id,
        String username,
        String passwordHash,
        String role,
        OffsetDateTime createdAt
) {
}
