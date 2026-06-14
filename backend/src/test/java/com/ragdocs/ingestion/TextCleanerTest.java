package com.ragdocs.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextCleanerTest {
    private final TextCleaner cleaner = new TextCleaner();

    @Test
    void normalizesWhitespaceAndInvisibleCharacters() {
        String cleaned = cleaner.clean("  hello\t world  \r\n\r\n\r\n second\u200B line  ");

        assertThat(cleaned).isEqualTo("hello world\n\nsecond line");
    }

    @Test
    void rejectsVeryShortParsedText() {
        assertThatThrownBy(() -> cleaner.validateQuality("too short"))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("少于 100");
    }
}
