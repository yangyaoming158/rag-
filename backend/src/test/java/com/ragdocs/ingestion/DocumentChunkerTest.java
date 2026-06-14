package com.ragdocs.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void mergesShortMarkdownSectionsAndKeepsHeadingPath() {
        ParsedDocument document = new ParsedDocument(DocumentKind.MARKDOWN, List.of(
                new ParsedBlock(textOf('a', 120), "架构设计 > 网关路由", null, null),
                new ParsedBlock(textOf('b', 260), "架构设计 > 网关路由", null, null)
        ));

        List<ChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).headingPath()).isEqualTo("架构设计 > 网关路由");
        assertThat(chunks.get(0).charLen()).isBetween(200, 1000);
        assertThat(chunks.get(0).chunkIndex()).isZero();
    }

    @Test
    void splitsLongMarkdownSectionWithOverlapAndBounds() {
        String content = String.join("\n\n",
                textOf('a', 360),
                textOf('b', 360),
                textOf('c', 360),
                textOf('d', 360)
        );
        ParsedDocument document = new ParsedDocument(DocumentKind.MARKDOWN, List.of(
                new ParsedBlock(content, "订单服务 > 状态机", null, null)
        ));

        List<ChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.headingPath()).isEqualTo("订单服务 > 状态机");
            assertThat(chunk.charLen()).isBetween(200, 1000);
        });
        assertThat(chunks.get(1).content()).contains(chunks.get(0).content()
                .substring(chunks.get(0).content().length() - DocumentChunker.OVERLAP_CHARS).strip());
    }

    @Test
    void splitsSingleLongParagraphWithoutTinyTrailingChunk() {
        ParsedDocument document = new ParsedDocument(DocumentKind.TEXT, List.of(
                new ParsedBlock(textOf('p', 970), null, null, null)
        ));

        List<ChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.charLen()).isBetween(200, 1000));
    }

    @Test
    void redistributesSmallTrailingMarkdownSectionWhenPreviousChunkIsNearLimit() {
        ParsedDocument document = new ParsedDocument(DocumentKind.MARKDOWN, List.of(
                new ParsedBlock(textOf('a', 880), "README", null, null),
                new ParsedBlock(textOf('z', 120), "README", null, null)
        ));

        List<ChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.charLen()).isBetween(200, 1000));
    }

    @Test
    void chunksPlainTextByParagraphsAndKeepsPageRange() {
        ParsedDocument document = new ParsedDocument(DocumentKind.PDF, List.of(
                new ParsedBlock(String.join("\n\n", textOf('a', 180), textOf('b', 180)), null, 1, 1),
                new ParsedBlock(String.join("\n\n", textOf('c', 180), textOf('d', 180), textOf('e', 180)), null, 2, 2)
        ));

        List<ChunkDraft> chunks = chunker.chunk(document);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.charLen()).isBetween(200, 1000));
        assertThat(chunks.get(0).pageStart()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).pageEnd()).isEqualTo(2);
    }

    @Test
    void rejectsDocumentsThatCannotProduceMinimumChunk() {
        ParsedDocument document = new ParsedDocument(DocumentKind.TEXT, List.of(
                new ParsedBlock(textOf('x', 120), null, null, null)
        ));

        assertThatThrownBy(() -> chunker.chunk(document))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("少于 200");
    }

    private String textOf(char ch, int length) {
        return String.valueOf(ch).repeat(length);
    }
}
