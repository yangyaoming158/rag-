package com.ragdocs.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull(message = "kbId 不能为空")
        Long kbId,

        @NotBlank(message = "审查类型不能为空")
        @Size(max = 64, message = "审查类型不能超过 64 字")
        String reviewType,

        @Size(max = 2000, message = "补充说明不能超过 2000 字")
        String supplement
) {
}
