package com.ragdocs.ingestion;

import java.util.List;

public record ParsedDocument(
        DocumentKind kind,
        List<ParsedBlock> blocks
) {

    public String fullText() {
        return blocks.stream()
                .map(ParsedBlock::text)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n\n" + right);
    }
}
