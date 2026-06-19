package com.ragdocs.rag;

import com.ragdocs.provider.ChatMessage;
import com.ragdocs.retrieval.RetrievalHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ReviewPromptBuilder {
    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final int MAX_CONTEXTS = 6;

    public PromptBuildResult build(ReviewTemplate template, String supplement, List<RetrievalHit> hits) {
        List<RagContext> contexts = contextsWithinBudget(hits);
        String prompt = """
                你是研发知识库与架构审查助手。你只能基于【参考资料】做审查，不允许编造资料外结论。

                【审查类型】
                %s

                【审查目标】
                %s

                【补充说明】
                %s

                【输出格式】
                风险等级: LOW|MEDIUM|HIGH|UNKNOWN
                审查结论:
                用 2-4 句话说明总体判断，并在关键判断后标注引用编号。
                发现的问题:
                - 每条问题必须说明依据，并标注引用编号。
                建议修改项:
                - 每条建议必须可执行，并标注引用编号。

                【参考资料】
                %s

                【历史对话】
                无

                【问题】
                请执行本次固定模板审查。若资料不足，请把风险等级设为 UNKNOWN，并明确缺少哪些文档；不要输出资料外事实。
                """.formatted(
                template.label(),
                template.description(),
                supplement == null || supplement.isBlank() ? "无" : supplement.strip(),
                references(contexts)
        );
        return new PromptBuildResult(
                List.of(
                        new ChatMessage("system", "你必须严格遵守用户消息中的审查格式、引用和拒答规则。"),
                        new ChatMessage("user", prompt)
                ),
                contexts,
                estimateTokens(prompt)
        );
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

    private int totalChars(List<RetrievalHit> hits) {
        return hits.stream().mapToInt(hit -> hit.content() == null ? 0 : hit.content().length()).sum();
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
