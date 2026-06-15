package com.ragdocs.rag;

import com.ragdocs.retrieval.RetrievalHit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {
    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildsContinuousReferenceNumbersAndKeepsTopSixContexts() {
        List<RetrievalHit> hits = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            hits.add(hit(i, 0.9 - i * 0.01, "content-" + i + " ".repeat(220)));
        }

        PromptBuildResult result = promptBuilder.build("question", hits, List.of());

        assertThat(result.contexts()).hasSize(6);
        assertThat(result.contexts()).extracting(RagContext::number).containsExactly(1, 2, 3, 4, 5, 6);
        String prompt = result.messages().get(1).content();
        assertThat(prompt).contains("[1] (来源: doc0.md > Heading 0)");
        assertThat(prompt).contains("[6] (来源: doc5.md > Heading 5)");
        assertThat(prompt).doesNotContain("doc6.md");
    }

    @Test
    void keepsOnlyRecentThreeRoundsAndTruncatesHistoryMessages() {
        List<RagHistoryMessage> history = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            history.add(new RagHistoryMessage(i % 2 == 0 ? "USER" : "ASSISTANT", "h" + i + "x".repeat(700)));
        }

        PromptBuildResult result = promptBuilder.build("question", List.of(hit(1, 0.8, "content")), history);

        String prompt = result.messages().get(1).content();
        assertThat(prompt).doesNotContain("h0");
        assertThat(prompt).doesNotContain("h1");
        assertThat(prompt).contains("h2");
        assertThat(prompt).contains("h7");
        assertThat(prompt).doesNotContain("x".repeat(501));
    }

    private RetrievalHit hit(int index, double similarity, String content) {
        return new RetrievalHit(
                index + 1L,
                100L + index,
                "doc" + index + ".md",
                index,
                content,
                "Heading " + index,
                null,
                null,
                content.length(),
                similarity
        );
    }
}
