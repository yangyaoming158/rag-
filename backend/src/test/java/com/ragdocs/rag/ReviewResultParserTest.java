package com.ragdocs.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewResultParserTest {
    private final ReviewResultParser parser = new ReviewResultParser();

    @Test
    void parsesStructuredReviewSections() {
        ParsedReviewResult result = parser.parse("""
                风险等级: HIGH
                审查结论:
                接口路径和任务验收口径存在明显不一致 [1]。
                发现的问题:
                - PRD 要求审核，接口文档缺少审核状态 [2]。
                建议修改项:
                - 补齐状态流转表并更新 API 契约 [1][2]。
                """);

        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.conclusion()).contains("接口路径");
        assertThat(result.issues()).contains("审核状态");
        assertThat(result.suggestions()).contains("状态流转表");
    }

    @Test
    void fallsBackWhenModelReturnsPlainText() {
        ParsedReviewResult result = parser.parse("根据当前知识库内容：没有明显冲突 [1]。");

        assertThat(result.riskLevel()).isEqualTo("UNKNOWN");
        assertThat(result.conclusion()).contains("没有明显冲突");
        assertThat(result.issues()).contains("未识别到结构化问题列表");
        assertThat(result.suggestions()).contains("未识别到结构化建议列表");
    }

    @Test
    void mapsChineseRiskLevel() {
        ParsedReviewResult result = parser.parse("""
                风险等级：高
                审查结论:
                缺少验收证据 [1]。
                """);

        assertThat(result.riskLevel()).isEqualTo("HIGH");
    }
}
