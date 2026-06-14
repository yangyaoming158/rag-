package com.ragdocs.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextCleaner {

    public String clean(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\n' || ch == '\t' || !Character.isISOControl(ch)) {
                builder.append(ch);
            }
        }
        String[] lines = builder.toString().split("\n", -1);
        List<String> cleanedLines = new ArrayList<>(lines.length);
        int blankCount = 0;
        for (String line : lines) {
            String cleanedLine = line.replaceAll("[\\u00A0\\u200B\\u200C\\u200D\\uFEFF]", " ")
                    .replaceAll("[ \\t]+", " ")
                    .strip();
            if (cleanedLine.isBlank()) {
                blankCount++;
                if (blankCount <= 1) {
                    cleanedLines.add("");
                }
            } else {
                blankCount = 0;
                cleanedLines.add(cleanedLine);
            }
        }
        return String.join("\n", cleanedLines).strip();
    }

    public void validateQuality(String cleanedText) {
        int visibleChars = cleanedText.replaceAll("\\s+", "").length();
        if (visibleChars < 100) {
            throw new IngestionException("解析文本少于 100 字，无法入库");
        }
        long replacementChars = cleanedText.chars().filter(ch -> ch == '\uFFFD').count();
        long invalidControls = cleanedText.chars()
                .filter(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\t')
                .count();
        double illegalRatio = visibleChars == 0 ? 1.0 : (replacementChars + invalidControls) / (double) visibleChars;
        if (illegalRatio > 0.30) {
            throw new IngestionException("解析文本非法字符占比超过 30%");
        }
    }
}
