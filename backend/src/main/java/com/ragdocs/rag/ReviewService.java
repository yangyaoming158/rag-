package com.ragdocs.rag;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.config.RetrievalProperties;
import com.ragdocs.domain.KnowledgeBase;
import com.ragdocs.domain.ReviewCitation;
import com.ragdocs.domain.ReviewReport;
import com.ragdocs.provider.ChatProvider;
import com.ragdocs.provider.ChatResult;
import com.ragdocs.provider.EmbeddingCallException;
import com.ragdocs.provider.EmbeddingProvider;
import com.ragdocs.repository.KnowledgeBaseRepository;
import com.ragdocs.repository.ModelCallLogRepository;
import com.ragdocs.repository.RetrievalRepository;
import com.ragdocs.repository.ReviewRepository;
import com.ragdocs.retrieval.RetrievalHit;
import com.ragdocs.web.dto.CitationDto;
import com.ragdocs.web.dto.CreateReviewRequest;
import com.ragdocs.web.dto.ReviewDto;
import com.ragdocs.web.dto.ReviewTypeDto;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewService {
    private static final int RETRIEVAL_TOP_K = 8;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RetrievalRepository retrievalRepository;
    private final ReviewRepository reviewRepository;
    private final ModelCallLogRepository modelCallLogRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ChatProvider chatProvider;
    private final RetrievalProperties retrievalProperties;
    private final ReviewPromptBuilder reviewPromptBuilder;
    private final ReviewResultParser reviewResultParser;
    private final CitationParser citationParser;

    public ReviewService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            RetrievalRepository retrievalRepository,
            ReviewRepository reviewRepository,
            ModelCallLogRepository modelCallLogRepository,
            EmbeddingProvider embeddingProvider,
            ChatProvider chatProvider,
            RetrievalProperties retrievalProperties,
            ReviewPromptBuilder reviewPromptBuilder,
            ReviewResultParser reviewResultParser,
            CitationParser citationParser
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.retrievalRepository = retrievalRepository;
        this.reviewRepository = reviewRepository;
        this.modelCallLogRepository = modelCallLogRepository;
        this.embeddingProvider = embeddingProvider;
        this.chatProvider = chatProvider;
        this.retrievalProperties = retrievalProperties;
        this.reviewPromptBuilder = reviewPromptBuilder;
        this.reviewResultParser = reviewResultParser;
        this.citationParser = citationParser;
    }

    public List<ReviewTypeDto> reviewTypes() {
        return ReviewTemplate.supportedTypes().stream()
                .map(type -> new ReviewTypeDto(type.code(), type.label(), type.description()))
                .toList();
    }

    public List<ReviewDto> listReviews(long ownerId, Long kbId) {
        if (kbId != null) {
            knowledgeBaseRepository.findByIdAndOwner(kbId, ownerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        }
        List<ReviewRepository.ReviewRow> rows = reviewRepository.listByOwner(ownerId, kbId);
        Map<Long, List<ReviewCitation>> citationsByReview = reviewRepository.findCitationsByReviewIds(
                rows.stream().map(row -> row.report().id()).toList()
        ).stream().collect(Collectors.groupingBy(ReviewCitation::reviewId));
        return rows.stream()
                .map(row -> toDto(row, citationsByReview.getOrDefault(row.report().id(), List.of())))
                .toList();
    }

    public ReviewDto getReview(long ownerId, long id) {
        ReviewRepository.ReviewRow row = reviewRepository.findByIdAndOwner(id, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审查记录不存在"));
        List<ReviewCitation> citations = reviewRepository.findCitationsByReviewIds(List.of(id));
        return toDto(row, citations);
    }

    public ReviewDto createReview(long ownerId, CreateReviewRequest request) {
        long start = System.nanoTime();
        KnowledgeBase kb = knowledgeBaseRepository.findByIdAndOwner(request.kbId(), ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        ReviewTemplate template = ReviewTemplate.fromCode(request.reviewType());
        String supplement = normalizeSupplement(request.supplement());
        List<RetrievalHit> hits = retrieve(kb.id(), template.query(supplement));

        if (hits.isEmpty() || !hasGroundedVectorHit(hits)) {
            ReviewReport report = reviewRepository.create(
                    ownerId,
                    kb.id(),
                    template.code(),
                    supplement,
                    "NO_ANSWER",
                    RagConstants.NO_ANSWER,
                    "UNKNOWN",
                    "当前知识库没有检索到足够相关的审查依据。",
                    "补充 PRD、接口文档、阶段计划或任务树文档后重试。",
                    null,
                    null,
                    null,
                    elapsedMillis(start)
            );
            return toDto(new ReviewRepository.ReviewRow(report, kb.name()), List.of());
        }

        PromptBuildResult prompt = reviewPromptBuilder.build(template, supplement, hits);
        ChatResult chatResult;
        long chatStart = System.nanoTime();
        try {
            chatResult = chatWithRetry(prompt);
        } catch (RuntimeException ex) {
            long latencyMs = elapsedMillis(start);
            ReviewReport report = reviewRepository.create(
                    ownerId,
                    kb.id(),
                    template.code(),
                    supplement,
                    "ERROR",
                    "LLM 调用失败: " + readableMessage(ex),
                    "UNKNOWN",
                    "模型调用失败，未生成审查问题。",
                    "检查 Provider 配置和模型调用日志后重试。",
                    null,
                    prompt.promptTokens(),
                    0,
                    latencyMs
            );
            modelCallLogRepository.recordChat(
                    null,
                    chatProvider.providerName(),
                    chatProvider.modelName(),
                    prompt.promptTokens(),
                    0,
                    elapsedMillis(chatStart),
                    "ERROR",
                    readableMessage(ex)
            );
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, report.conclusion());
        }

        CitationParseResult citations = citationParser.parse(chatResult.content(), prompt.contexts());
        ParsedReviewResult parsed = reviewResultParser.parse(chatResult.content());
        String status = reviewStatus(chatResult.content(), citations);
        long latencyMs = elapsedMillis(start);
        ReviewReport report = reviewRepository.create(
                ownerId,
                kb.id(),
                template.code(),
                supplement,
                status,
                parsed.conclusion(),
                parsed.riskLevel(),
                parsed.issues(),
                parsed.suggestions(),
                citations.warning(),
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                latencyMs
        );
        reviewRepository.insertCitations(report.id(), citations.citations());
        modelCallLogRepository.recordChat(
                null,
                chatProvider.providerName(),
                chatProvider.modelName(),
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                elapsedMillis(chatStart),
                "OK",
                null
        );
        List<ReviewCitation> persistedCitations = reviewRepository.findCitationsByReviewIds(List.of(report.id()));
        return toDto(new ReviewRepository.ReviewRow(report, kb.name()), persistedCitations);
    }

    private List<RetrievalHit> retrieve(long kbId, String query) {
        long start = System.nanoTime();
        int promptTokens = estimateTokens(query);
        try {
            List<float[]> embeddings = embeddingProvider.embed(List.of(query));
            modelCallLogRepository.recordEmbedding(
                    null,
                    embeddingProvider.providerName(),
                    embeddingProvider.modelName(),
                    promptTokens,
                    elapsedMillis(start),
                    "OK",
                    null
            );
            if (embeddings.size() != 1) {
                throw new EmbeddingCallException("Embedding 响应数量不匹配");
            }
            float[] vector = embeddings.get(0);
            validateDimensions(vector);
            return retrievalRepository.search(kbId, vector, query, RETRIEVAL_TOP_K);
        } catch (RuntimeException ex) {
            modelCallLogRepository.recordEmbedding(
                    null,
                    embeddingProvider.providerName(),
                    embeddingProvider.modelName(),
                    promptTokens,
                    elapsedMillis(start),
                    "ERROR",
                    ex.getMessage()
            );
            throw new BusinessException(ErrorCode.EMBEDDING_CALL_FAILED, readableMessage(ex));
        }
    }

    private boolean hasGroundedVectorHit(List<RetrievalHit> hits) {
        return hits.stream()
                .anyMatch(hit -> hit.similarity() >= retrievalProperties.minSimilarity());
    }

    private void validateDimensions(float[] vector) {
        if (vector.length != embeddingProvider.dimensions()) {
            throw new EmbeddingCallException("Embedding 维度不匹配: expected="
                    + embeddingProvider.dimensions() + ", actual=" + vector.length);
        }
    }

    private ChatResult chatWithRetry(PromptBuildResult prompt) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return chatProvider.chat(prompt.messages());
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        throw last == null ? new IllegalStateException("LLM 调用失败") : last;
    }

    private String reviewStatus(String answer, CitationParseResult citations) {
        if (RagConstants.NO_ANSWER.equals(answer == null ? "" : answer.strip())) {
            return "NO_ANSWER";
        }
        if (citations.citations().isEmpty()) {
            return "UNGROUNDED";
        }
        return "OK";
    }

    private ReviewDto toDto(ReviewRepository.ReviewRow row, List<ReviewCitation> citations) {
        ReviewReport report = row.report();
        ReviewTemplate template = ReviewTemplate.fromCode(report.reviewType());
        return new ReviewDto(
                report.id(),
                report.kbId(),
                row.kbName(),
                report.reviewType(),
                template.label(),
                report.supplement(),
                report.status(),
                report.conclusion(),
                report.riskLevel(),
                report.issues(),
                report.suggestions(),
                citations.stream()
                        .sorted(Comparator.comparingInt(ReviewCitation::rank))
                        .map(this::toDto)
                        .toList(),
                report.citationWarning(),
                report.promptTokens(),
                report.completionTokens(),
                report.latencyMs(),
                report.createdAt()
        );
    }

    private CitationDto toDto(ReviewCitation citation) {
        return new CitationDto(
                citation.rank(),
                citation.chunkId(),
                citation.documentFilename(),
                citation.headingPath(),
                citation.snippet(),
                citation.similarity()
        );
    }

    private String normalizeSupplement(String supplement) {
        if (supplement == null || supplement.isBlank()) {
            return null;
        }
        String normalized = supplement.strip();
        if (normalized.length() > 2000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "补充说明不能超过 2000 字");
        }
        return normalized;
    }

    private int estimateTokens(String text) {
        return Math.max(1, text == null ? 1 : text.length() / 4);
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }

    private String readableMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "模型调用失败" : message;
    }
}
