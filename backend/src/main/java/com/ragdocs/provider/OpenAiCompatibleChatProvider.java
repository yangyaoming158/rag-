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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleChatProvider implements ChatProvider {
    private final AiProperties.Chat properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatProvider(AiProperties.Chat properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatResult chat(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ChatCallException("Chat 输入不能为空");
        }
        if (properties.baseUrl().isBlank()) {
            throw new ChatCallException("Chat base-url 未配置");
        }
        if (properties.apiKey().isBlank()) {
            throw new ChatCallException("Chat api-key 未配置");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.model(),
                    "temperature", properties.temperature(),
                    "messages", messages.stream()
                            .map(message -> Map.of("role", message.role(), "content", message.content()))
                            .toList()
            ));
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ChatCallException("Chat 调用失败: HTTP " + response.statusCode());
            }
            return parse(response.body(), messages);
        } catch (IOException ex) {
            throw new ChatCallException("Chat 响应解析失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ChatCallException("Chat 调用被中断", ex);
        }
    }

    @Override
    public String providerName() {
        return properties.provider();
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    private URI endpoint() {
        String base = properties.baseUrl().endsWith("/")
                ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
                : properties.baseUrl();
        if (base.endsWith("/chat/completions")) {
            return URI.create(base);
        }
        return URI.create(base + "/chat/completions");
    }

    private ChatResult parse(String body, List<ChatMessage> messages) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new ChatCallException("Chat 响应缺少 choices");
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new ChatCallException("Chat 响应内容为空");
        }
        JsonNode usage = root.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(estimateTokens(messages));
        int completionTokens = usage.path("completion_tokens").asInt(estimateTokens(content.asText()));
        return new ChatResult(content.asText(), promptTokens, completionTokens);
    }

    private int estimateTokens(List<ChatMessage> messages) {
        return Math.max(1, messages.stream().mapToInt(message -> message.content().length()).sum() / 4);
    }

    private int estimateTokens(String text) {
        return Math.max(1, text == null ? 1 : text.length() / 4);
    }
}
