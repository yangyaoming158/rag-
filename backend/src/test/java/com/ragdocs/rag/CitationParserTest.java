package com.ragdocs.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationParserTest {
    private final CitationParser parser = new CitationParser();

    @Test
    void mapsLegalCitationsAndIgnoresInvalidNumbers() {
        CitationParseResult result = parser.parse("答案引用 [2][9][2]", contexts());

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).chunkId()).isEqualTo(20L);
        assertThat(result.citations().get(0).rank()).isEqualTo(1);
        assertThat(result.warning()).contains("[9]");
    }

    @Test
    void returnsNoCitationsForUngroundedAnswer() {
        CitationParseResult result = parser.parse("答案没有引用", contexts());

        assertThat(result.citations()).isEmpty();
        assertThat(result.warning()).isNull();
    }

    private List<RagContext> contexts() {
        return List.of(
                new RagContext(1, 10L, 100L, "a.md", "A", null, null, "a".repeat(600), 0.7),
                new RagContext(2, 20L, 200L, "b.md", "B", null, null, "b".repeat(600), 0.6)
        );
    }
}
