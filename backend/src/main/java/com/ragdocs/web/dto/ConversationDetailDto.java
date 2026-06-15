package com.ragdocs.web.dto;

import java.util.List;

public record ConversationDetailDto(
        ConversationDto conversation,
        List<MessageDto> messages
) {
}
