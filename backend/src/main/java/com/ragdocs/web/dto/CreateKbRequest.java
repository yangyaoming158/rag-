package com.ragdocs.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKbRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过 128 个字符")
        String name,

        @Size(max = 1000, message = "知识库描述不能超过 1000 个字符")
        String description
) {
}
