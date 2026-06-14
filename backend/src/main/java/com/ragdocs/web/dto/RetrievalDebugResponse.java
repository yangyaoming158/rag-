package com.ragdocs.web.dto;

import java.util.List;

public record RetrievalDebugResponse(
        String query,
        int topK,
        double minSimilarity,
        List<RetrievalHitDto> hits
) {
}
