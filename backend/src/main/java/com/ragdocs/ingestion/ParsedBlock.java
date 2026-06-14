package com.ragdocs.ingestion;

public record ParsedBlock(
        String text,
        String headingPath,
        Integer pageStart,
        Integer pageEnd
) {
}
