package com.ragdocs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval")
public record RetrievalProperties(double minSimilarity, int defaultTopK, int maxTopK) {

    public RetrievalProperties {
        minSimilarity = minSimilarity <= 0 ? 0.35 : minSimilarity;
        defaultTopK = defaultTopK <= 0 ? 8 : defaultTopK;
        maxTopK = maxTopK <= 0 ? 20 : maxTopK;
    }
}
