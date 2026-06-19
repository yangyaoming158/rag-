package com.ragdocs.rag;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;

import java.util.Arrays;
import java.util.List;

public enum ReviewTemplate {
    PRD_API_CONSISTENCY(
            "PRD_API_CONSISTENCY",
            "PRD 与接口文档一致性检查",
            "检查需求描述、接口路径、请求响应、状态码、权限和验收口径是否存在冲突。",
            "PRD 接口文档 API 契约 路径 请求 响应 状态码 权限 验收 一致性"
    ),
    TASK_TREE_RISK(
            "TASK_TREE_RISK",
            "任务树遗漏风险检查",
            "检查阶段计划、任务树、验收 Gate、失败路径和联调步骤是否有遗漏。",
            "任务树 阶段计划 TaskMaster Phase Gate 验收 风险 遗漏 联调 失败路径"
    );

    private final String code;
    private final String label;
    private final String description;
    private final String querySeed;

    ReviewTemplate(String code, String label, String description, String querySeed) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.querySeed = querySeed;
    }

    public static ReviewTemplate fromCode(String code) {
        return Arrays.stream(values())
                .filter(template -> template.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "不支持的审查类型"));
    }

    public static List<ReviewTemplate> supportedTypes() {
        return List.of(values());
    }

    public String query(String supplement) {
        if (supplement == null || supplement.isBlank()) {
            return querySeed;
        }
        return querySeed + " " + supplement.strip();
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
