package com.ragdocs.retrieval;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.config.RetrievalProperties;
import com.ragdocs.provider.EmbeddingCallException;
import com.ragdocs.provider.EmbeddingProvider;
import com.ragdocs.repository.KnowledgeBaseRepository;
import com.ragdocs.repository.ModelCallLogRepository;
import com.ragdocs.repository.RetrievalRepository;
import com.ragdocs.web.dto.RetrievalDebugRequest;
import com.ragdocs.web.dto.RetrievalDebugResponse;
import com.ragdocs.web.dto.RetrievalHitDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RetrievalService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RetrievalRepository retrievalRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ModelCallLogRepository modelCallLogRepository;
    private final RetrievalProperties retrievalProperties;

    public RetrievalService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            RetrievalRepository retrievalRepository,
            EmbeddingProvider embeddingProvider,
            ModelCallLogRepository modelCallLogRepository,
            RetrievalProperties retrievalProperties
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.retrievalRepository = retrievalRepository;
        this.embeddingProvider = embeddingProvider;
        this.modelCallLogRepository = modelCallLogRepository;
        this.retrievalProperties = retrievalProperties;
    }

    public RetrievalDebugResponse debug(long ownerId, long kbId, RetrievalDebugRequest request) {
        knowledgeBaseRepository.findByIdAndOwner(kbId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        String query = normalizeQuery(request.query());
        int topK = normalizeTopK(request.topK());
        float[] queryVector = embedQuery(query);
        List<RetrievalHit> hits = retrievalRepository.search(kbId, queryVector, topK);
        return new RetrievalDebugResponse(
                query,
                topK,
                retrievalProperties.minSimilarity(),
                toDtos(hits)
        );
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "query 不能为空");
        }
        return query.strip();
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return retrievalProperties.defaultTopK();
        }
        if (topK < 1 || topK > retrievalProperties.maxTopK()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "topK 必须在 1-" + retrievalProperties.maxTopK() + " 之间");
        }
        return topK;
    }

    private float[] embedQuery(String query) {
        long start = System.nanoTime();
        int promptTokens = estimateTokens(List.of(query));
        try {
            List<float[]> embeddings = embeddingProvider.embed(List.of(query));
            long latencyMs = elapsedMillis(start);
            modelCallLogRepository.recordEmbedding(
                    null,
                    embeddingProvider.providerName(),
                    embeddingProvider.modelName(),
                    promptTokens,
                    latencyMs,
                    "OK",
                    null
            );
            if (embeddings.size() != 1) {
                throw new EmbeddingCallException("Embedding 响应数量不匹配");
            }
            float[] vector = embeddings.get(0);
            validateDimensions(vector);
            return vector;
        } catch (RuntimeException ex) {
            long latencyMs = elapsedMillis(start);
            modelCallLogRepository.recordEmbedding(
                    null,
                    embeddingProvider.providerName(),
                    embeddingProvider.modelName(),
                    promptTokens,
                    latencyMs,
                    "ERROR",
                    ex.getMessage()
            );
            throw new BusinessException(ErrorCode.EMBEDDING_CALL_FAILED, readableMessage(ex));
        }
    }

    private void validateDimensions(float[] vector) {
        if (vector.length != embeddingProvider.dimensions()) {
            throw new EmbeddingCallException("Embedding 维度不匹配: expected="
                    + embeddingProvider.dimensions() + ", actual=" + vector.length);
        }
    }

    private List<RetrievalHitDto> toDtos(List<RetrievalHit> hits) {
        List<RetrievalHitDto> dtos = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            RetrievalHit hit = hits.get(i);
            dtos.add(new RetrievalHitDto(
                    i + 1,
                    hit.chunkId(),
                    hit.documentId(),
                    hit.documentFilename(),
                    hit.chunkIndex(),
                    hit.headingPath(),
                    hit.pageStart(),
                    hit.pageEnd(),
                    hit.charLen(),
                    hit.similarity(),
                    hit.similarity() >= retrievalProperties.minSimilarity(),
                    preview(hit.content())
            ));
        }
        return dtos;
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").strip();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }

    private int estimateTokens(List<String> texts) {
        int chars = texts.stream().mapToInt(text -> text == null ? 0 : text.length()).sum();
        return Math.max(1, chars / 4);
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }

    private String readableMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "Embedding 调用失败" : message;
    }
}
