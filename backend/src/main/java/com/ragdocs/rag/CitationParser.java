package com.ragdocs.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CitationParser {
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    public CitationParseResult parse(String answer, List<RagContext> contexts) {
        Map<Integer, RagContext> byNumber = contexts.stream()
                .collect(Collectors.toMap(RagContext::number, context -> context));
        Set<Integer> legalNumbers = new LinkedHashSet<>();
        Set<Integer> illegalNumbers = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (byNumber.containsKey(number)) {
                legalNumbers.add(number);
            } else {
                illegalNumbers.add(number);
            }
        }

        List<CitationDraft> citations = new ArrayList<>(legalNumbers.size());
        int rank = 1;
        for (Integer number : legalNumbers) {
            RagContext context = byNumber.get(number);
            citations.add(new CitationDraft(
                    rank++,
                    context.chunkId(),
                    context.documentFilename(),
                    context.headingPath(),
                    snippet(context.content()),
                    context.similarity()
            ));
        }
        return new CitationParseResult(citations, warning(illegalNumbers));
    }

    private String warning(Set<Integer> illegalNumbers) {
        if (illegalNumbers.isEmpty()) {
            return null;
        }
        String ignored = illegalNumbers.stream()
                .map(number -> "[" + number + "]")
                .collect(Collectors.joining(", "));
        return "已忽略不存在的引用编号: " + ignored;
    }

    private String snippet(String content) {
        String compact = content == null ? "" : content.replaceAll("\\s+", " ").strip();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
