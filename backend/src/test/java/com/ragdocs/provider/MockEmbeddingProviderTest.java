package com.ragdocs.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmbeddingProviderTest {

    @Test
    void returnsDeterministicVectorsWithConfiguredDimensions() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(1024, "mock-bge-m3");

        float[] first = provider.embed(List.of("order service status machine")).get(0);
        float[] second = provider.embed(List.of("order service status machine")).get(0);

        assertThat(first).hasSize(1024);
        assertThat(second).containsExactly(first);
    }

    @Test
    void givesHigherScoreToSharedTermsThanUnrelatedText() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(1024, "mock-bge-m3");
        float[] query = provider.embed(List.of("订单 状态机 order status")).get(0);
        float[] related = provider.embed(List.of("订单服务 order service status machine 状态流转")).get(0);
        float[] unrelated = provider.embed(List.of("前端上传按钮 layout css element plus")).get(0);

        assertThat(dot(query, related)).isGreaterThan(dot(query, unrelated));
    }

    private double dot(float[] left, float[] right) {
        double value = 0;
        for (int i = 0; i < left.length; i++) {
            value += left[i] * right[i];
        }
        return value;
    }
}
