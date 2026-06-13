package com.ragdocs.domain;

import java.time.OffsetDateTime;

public record Document(
        long id,
        long kbId,
        String originalFilename,
        String contentType,
        long fileSize,
        String storagePath,
        String sha256,
        String status,
        String errorMessage,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
