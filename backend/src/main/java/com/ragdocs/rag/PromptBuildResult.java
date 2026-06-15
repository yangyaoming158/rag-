package com.ragdocs.rag;

import com.ragdocs.provider.ChatMessage;

import java.util.List;

public record PromptBuildResult(List<ChatMessage> messages, List<RagContext> contexts, int promptTokens) {
}
