package com.ragdocs.web.dto;

import java.time.OffsetDateTime;

public record DocumentDto(
        long id,
        long kbId,
        String originalFilename,
        String contentType,
        long fileSize,
        String status,
        String errorMessage,
        int chunkCount,
        Long jobId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
