package com.ragdocs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragdocs.config.AiProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {
    private final AiProperties.Embedding properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingProvider(AiProperties.Embedding properties, ObjectMapper objectMapper) {
        if (properties.baseUrl().isBlank()) {
            throw new EmbeddingCallException("Embedding base-url 未配置");
        }
        if (properties.apiKey().isBlank()) {
            throw new EmbeddingCallException("Embedding api-key 未配置");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new EmbeddingCallException("Embedding 输入不能为空");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.model(),
                    "input", texts
            ));
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmbeddingCallException("Embedding 调用失败: HTTP " + response.statusCode());
            }
            return parseEmbeddings(response.body(), texts.size());
        } catch (IOException ex) {
            throw new EmbeddingCallException("Embedding 响应解析失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmbeddingCallException("Embedding 调用被中断", ex);
        }
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    @Override
    public String providerName() {
        return properties.provider();
    }

    private URI endpoint() {
        String base = properties.baseUrl().endsWith("/")
                ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
                : properties.baseUrl();
        if (base.endsWith("/embeddings")) {
            return URI.create(base);
        }
        return URI.create(base + "/embeddings");
    }

    private List<float[]> parseEmbeddings(String body, int expectedSize) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new EmbeddingCallException("Embedding 响应缺少 data 数组");
        }
        Map<Integer, float[]> byIndex = new HashMap<>();
        List<float[]> ordered = new ArrayList<>();
        int position = 0;
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new EmbeddingCallException("Embedding 响应缺少 embedding 数组");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            JsonNode index = item.get("index");
            if (index != null && index.canConvertToInt()) {
                byIndex.put(index.asInt(), vector);
            } else {
                ordered.add(vector);
            }
            position++;
        }
        if (!byIndex.isEmpty()) {
            ordered.clear();
            for (int i = 0; i < expectedSize; i++) {
                float[] vector = byIndex.get(i);
                if (vector == null) {
                    throw new EmbeddingCallException("Embedding 响应缺少 index=" + i);
                }
                ordered.add(vector);
            }
        }
        if (ordered.size() != expectedSize) {
            throw new EmbeddingCallException("Embedding 响应数量不匹配: expected=" + expectedSize + ", actual=" + ordered.size());
        }
        return ordered;
    }
}
