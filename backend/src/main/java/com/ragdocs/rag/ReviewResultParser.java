package com.ragdocs.rag;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReviewResultParser {
    private static final Pattern RISK_PATTERN = Pattern.compile(
            "(?m)^\\s*风险等级\\s*[:：]\\s*(LOW|MEDIUM|HIGH|UNKNOWN|低|中|高).*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?m)^\\s*(审查结论|发现的问题|建议修改项)\\s*[:：]?\\s*$"
    );

    public ParsedReviewResult parse(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            return new ParsedReviewResult("UNKNOWN", "模型未返回审查内容。", "无", "补充审查说明后重试。");
        }
        return new ParsedReviewResult(
                riskLevel(normalized),
                section(normalized, "审查结论", normalized),
                section(normalized, "发现的问题", "未识别到结构化问题列表；请查看审查结论。"),
                section(normalized, "建议修改项", "未识别到结构化建议列表；请查看审查结论。")
        );
    }

    private String riskLevel(String content) {
        Matcher matcher = RISK_PATTERN.matcher(content);
        if (!matcher.find()) {
            return "UNKNOWN";
        }
        return switch (matcher.group(1).toUpperCase(Locale.ROOT)) {
            case "LOW", "低" -> "LOW";
            case "MEDIUM", "中" -> "MEDIUM";
            case "HIGH", "高" -> "HIGH";
            default -> "UNKNOWN";
        };
    }

    private String section(String content, String heading, String fallback) {
        Matcher matcher = HEADING_PATTERN.matcher(content);
        int start = -1;
        int end = content.length();
        while (matcher.find()) {
            if (heading.equals(matcher.group(1))) {
                start = matcher.end();
                if (matcher.find()) {
                    end = matcher.start();
                }
                break;
            }
        }
        if (start < 0) {
            return fallback;
        }
        String value = content.substring(start, end).strip();
        return value.isEmpty() ? fallback : value;
    }
}
