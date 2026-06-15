package com.ragdocs.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotNull(message = "kbId 不能为空")
        Long kbId,
        @Size(max = 120, message = "title 不能超过 120 字")
        String title
) {
}
