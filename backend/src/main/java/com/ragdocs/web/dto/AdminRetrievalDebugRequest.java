package com.ragdocs.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminRetrievalDebugRequest(
        @NotNull(message = "kbId 不能为空")
        Long kbId,

        @Size(max = 2000, message = "query 不能超过 2000 字")
        String query,

        Integer topK
) {
}
