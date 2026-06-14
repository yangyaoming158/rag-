package com.ragdocs.ingestion;

import com.ragdocs.domain.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDocumentParserTest {
    private final DefaultDocumentParser parser = new DefaultDocumentParser(new TextCleaner());

    @Test
    void parsesMarkdownHeadingBlocks() {
        String markdown = """
                # 架构设计

                %s

                ## 网关路由

                %s
                """.formatted("a".repeat(120), "b".repeat(160));
        Document document = document("architecture.md", "text/markdown");

        ParsedDocument parsed = parser.parse(document, new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

        assertThat(parsed.kind()).isEqualTo(DocumentKind.MARKDOWN);
        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).headingPath()).isEqualTo("架构设计");
        assertThat(parsed.blocks().get(1).headingPath()).isEqualTo("架构设计 > 网关路由");
    }

    private Document document(String filename, String contentType) {
        return new Document(
                1L,
                1L,
                filename,
                contentType,
                1024L,
                "1/1.md",
                "sha",
                "UPLOADED",
                null,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
