package com.ragdocs.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.storage")
public record StorageProperties(String root) {

    public StorageProperties {
        if (root == null || root.isBlank()) {
            root = "./data/files";
        }
    }
}
