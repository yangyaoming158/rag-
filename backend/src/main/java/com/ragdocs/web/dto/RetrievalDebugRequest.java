package com.ragdocs.web.dto;

import jakarta.validation.constraints.Size;

public record RetrievalDebugRequest(
        @Size(max = 2000, message = "query 不能超过 2000 字")
        String query,
        Integer topK
) {
}
