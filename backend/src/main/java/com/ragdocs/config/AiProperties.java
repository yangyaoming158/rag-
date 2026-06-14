package com.ragdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.ai")
public record AiProperties(Chat chat, Embedding embedding) {

    public AiProperties {
        if (chat == null) {
            chat = new Chat("mock", "", "", "mock-chat");
        }
        if (embedding == null) {
            embedding = new Embedding("mock", "", "", "mock-bge-m3", 1024, 32);
        }
    }

    public record Chat(String provider, String baseUrl, String apiKey, String model) {
    }

    public record Embedding(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            int dimensions,
            int maxBatchSize
    ) {
        public Embedding {
            provider = blankDefault(provider, "mock");
            baseUrl = blankDefault(baseUrl, "");
            apiKey = blankDefault(apiKey, "");
            model = blankDefault(model, "mock-bge-m3");
            dimensions = dimensions <= 0 ? 1024 : dimensions;
            maxBatchSize = maxBatchSize <= 0 ? 32 : Math.min(maxBatchSize, 32);
        }
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
