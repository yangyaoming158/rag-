package com.ragdocs.config;

import com.ragdocs.auth.JwtProperties;
import com.ragdocs.provider.ChatProvider;
import com.ragdocs.provider.EmbeddingProvider;
import com.ragdocs.provider.MockChatProvider;
import com.ragdocs.provider.MockEmbeddingProvider;
import com.ragdocs.provider.OpenAiCompatibleChatProvider;
import com.ragdocs.provider.OpenAiCompatibleEmbeddingProvider;
import com.ragdocs.service.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, StorageProperties.class, AiProperties.class, RetrievalProperties.class})
@EnableAsync
public class AppConfiguration {

    @Bean
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ingestion-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean
    public EmbeddingProvider embeddingProvider(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Embedding embedding = properties.embedding();
        if ("mock".equalsIgnoreCase(embedding.provider())) {
            return new MockEmbeddingProvider(embedding.dimensions(), embedding.model());
        }
        return new OpenAiCompatibleEmbeddingProvider(embedding, objectMapper);
    }

    @Bean
    public ChatProvider chatProvider(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Chat chat = properties.chat();
        if ("mock".equalsIgnoreCase(chat.provider())) {
            return new MockChatProvider(chat.model());
        }
        return new OpenAiCompatibleChatProvider(chat, objectMapper);
    }
}
