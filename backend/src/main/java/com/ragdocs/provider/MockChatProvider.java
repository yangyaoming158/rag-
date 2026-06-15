package com.ragdocs.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockChatProvider implements ChatProvider {
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\[(\\d+)] \\(来源: ([^)]+)\\) ([\\s\\S]*?)(?=\\n\\[\\d+] \\(来源: |\\n\\n【历史对话】|\\z)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile("【问题】([\\s\\S]*)");

    private final String modelName;

    public MockChatProvider(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public ChatResult chat(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ChatCallException("Chat 输入不能为空");
        }
        String prompt = messages.get(messages.size() - 1).content();
        String question = extractQuestion(prompt);
        List<Reference> references = extractReferences(prompt);
        if (references.isEmpty()) {
            return result("根据当前知识库内容，无法回答这个问题。", prompt);
        }
        List<String> queryTerms = terms(question);
        List<Reference> selected = references.stream()
                .sorted(Comparator.comparingInt((Reference ref) -> score(ref.text(), queryTerms)).reversed())
                .limit(2)
                .toList();
        StringBuilder answer = new StringBuilder();
        answer.append("根据当前知识库内容：");
        for (int i = 0; i < selected.size(); i++) {
            Reference ref = selected.get(i);
            if (i > 0) {
                answer.append("；");
            }
            answer.append(compact(ref.text(), 180)).append(" [").append(ref.number()).append("]");
        }
        answer.append("。");
        return result(answer.toString(), prompt);
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private ChatResult result(String content, String prompt) {
        return new ChatResult(content, estimateTokens(prompt), estimateTokens(content));
    }

    private List<Reference> extractReferences(String prompt) {
        List<Reference> references = new ArrayList<>();
        Matcher matcher = REFERENCE_PATTERN.matcher(prompt);
        while (matcher.find()) {
            references.add(new Reference(
                    Integer.parseInt(matcher.group(1)),
                    matcher.group(2),
                    matcher.group(3).strip()
            ));
        }
        return references;
    }

    private String extractQuestion(String prompt) {
        Matcher matcher = QUESTION_PATTERN.matcher(prompt);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private List<String> terms(String text) {
        List<String> values = new ArrayList<>();
        for (String term : text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")) {
            if (term.length() >= 2) {
                values.add(term);
            }
        }
        return values;
    }

    private int score(String text, List<String> terms) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (lower.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String compact(String text, int maxChars) {
        String compact = text.replaceAll("\\s+", " ").strip();
        return compact.length() <= maxChars ? compact : compact.substring(0, maxChars);
    }

    private int estimateTokens(String text) {
        return Math.max(1, text == null ? 1 : text.length() / 4);
    }

    private record Reference(int number, String source, String text) {
    }
}
