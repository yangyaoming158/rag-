package com.ragdocs.rag;

import com.ragdocs.provider.ChatMessage;
import com.ragdocs.retrieval.RetrievalHit;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class PromptBuilder {
    static final int MAX_CONTEXT_CHARS = 6000;
    static final int MAX_CONTEXTS = 6;
    static final int MAX_HISTORY_MESSAGES = 6;
    static final int MAX_HISTORY_CHARS = 500;

    private final String template;

    public PromptBuilder() {
        this.template = loadTemplate();
    }

    public PromptBuildResult build(String question, List<RetrievalHit> hits, List<RagHistoryMessage> history) {
        List<RagContext> contexts = contextsWithinBudget(hits);
        String prompt = template
                .replace("{references}", references(contexts))
                .replace("{history}", history(history))
                .replace("{question}", question.strip());
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "你必须严格遵守用户消息中的引用和拒答规则。"),
                new ChatMessage("user", prompt)
        );
        return new PromptBuildResult(messages, contexts, estimateTokens(prompt));
    }

    private List<RagContext> contextsWithinBudget(List<RetrievalHit> hits) {
        List<RetrievalHit> candidates = new ArrayList<>(hits.stream().limit(MAX_CONTEXTS).toList());
        while (totalChars(candidates) > MAX_CONTEXT_CHARS && !candidates.isEmpty()) {
            RetrievalHit lowest = candidates.stream()
                    .min(Comparator.comparingDouble(RetrievalHit::similarity))
                    .orElse(candidates.get(candidates.size() - 1));
            candidates.remove(lowest);
        }
        List<RagContext> contexts = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalHit hit = candidates.get(i);
            contexts.add(new RagContext(
                    i + 1,
                    hit.chunkId(),
                    hit.documentId(),
                    hit.documentFilename(),
                    hit.headingPath(),
                    hit.pageStart(),
                    hit.pageEnd(),
                    hit.content(),
                    hit.similarity()
            ));
        }
        return contexts;
    }

    private int totalChars(List<RetrievalHit> hits) {
        return hits.stream().mapToInt(hit -> hit.content() == null ? 0 : hit.content().length()).sum();
    }

    private String references(List<RagContext> contexts) {
        if (contexts.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (RagContext context : contexts) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("[")
                    .append(context.number())
                    .append("] (来源: ")
                    .append(source(context))
                    .append(") ")
                    .append(context.content().strip());
        }
        return builder.toString();
    }

    private String source(RagContext context) {
        StringBuilder builder = new StringBuilder(context.documentFilename());
        if (context.headingPath() != null && !context.headingPath().isBlank()) {
            builder.append(" > ").append(context.headingPath());
        }
        if (context.pageStart() != null) {
            builder.append(" p.").append(context.pageStart());
            if (context.pageEnd() != null && !context.pageEnd().equals(context.pageStart())) {
                builder.append("-").append(context.pageEnd());
            }
        }
        return builder.toString();
    }

    private String history(List<RagHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        List<RagHistoryMessage> recent = history.size() <= MAX_HISTORY_MESSAGES
                ? history
                : history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
        StringBuilder builder = new StringBuilder();
        for (RagHistoryMessage message : recent) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append(message.role()).append(": ").append(truncate(message.content(), MAX_HISTORY_CHARS));
        }
        return builder.toString();
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() <= maxChars ? stripped : stripped.substring(0, maxChars);
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private String loadTemplate() {
        try {
            return new ClassPathResource("prompts/rag-answer-v1.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("RAG prompt 模板加载失败", ex);
        }
    }
}
