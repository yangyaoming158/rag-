package com.ragdocs.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQaFeedbackRequest(
        @NotBlank(message = "rating 不能为空")
        String rating,

        @Size(max = 120, message = "reason 不能超过 120 字")
        String reason,

        @Size(max = 1000, message = "comment 不能超过 1000 字")
        String comment
) {
}
