package com.ragdocs.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "question 不能为空")
        @Size(max = 2000, message = "question 不能超过 2000 字")
        String question
) {
}
