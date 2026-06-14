package com.ragdocs.provider;

import java.util.List;

public interface EmbeddingProvider {
    List<float[]> embed(List<String> texts);

    int dimensions();

    String modelName();

    String providerName();
}
