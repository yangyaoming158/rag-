package com.ragdocs.rag;

import java.util.List;

public record CitationParseResult(List<CitationDraft> citations, String warning) {
}
