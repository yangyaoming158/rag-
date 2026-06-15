package com.ragdocs.provider;

public record ChatResult(String content, int promptTokens, int completionTokens) {
}
