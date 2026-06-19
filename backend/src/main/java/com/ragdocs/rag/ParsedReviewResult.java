package com.ragdocs.rag;

public record ParsedReviewResult(
        String riskLevel,
        String conclusion,
        String issues,
        String suggestions
) {
}
