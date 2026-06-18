package com.ragdocs.rag;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.config.RetrievalProperties;
import com.ragdocs.domain.Citation;
import com.ragdocs.domain.Conversation;
import com.ragdocs.domain.RagMessage;
import com.ragdocs.provider.ChatProvider;
import com.ragdocs.provider.ChatResult;
import com.ragdocs.provider.EmbeddingCallException;
import com.ragdocs.provider.EmbeddingProvider;
import com.ragdocs.repository.CitationRepository;
import com.ragdocs.repository.ConversationRepository;
import com.ragdocs.repository.KnowledgeBaseRepository;
import com.ragdocs.repository.MessageRepository;
import com.ragdocs.repository.ModelCallLogRepository;
import com.ragdocs.repository.RetrievalRepository;
import com.ragdocs.retrieval.RetrievalHit;
import com.ragdocs.web.dto.CitationDto;
import com.ragdocs.web.dto.ConversationDetailDto;
import com.ragdocs.web.dto.ConversationDto;
import com.ragdocs.web.dto.CreateConversationRequest;
import com.ragdocs.web.dto.MessageDto;
import com.ragdocs.web.dto.RagAnswerDto;
import com.ragdocs.web.dto.SendMessageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {
    private static final int RETRIEVAL_TOP_K = 8;
    private static final int HISTORY_LIMIT = 6;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CitationRepository citationRepository;
    private final RetrievalRepository retrievalRepository;
    private final ModelCallLogRepository modelCallLogRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ChatProvider chatProvider;
    private final RetrievalProperties retrievalProperties;
    private final PromptBuilder promptBuilder;
    private final CitationParser citationParser;

    public RagService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            CitationRepository citationRepository,
            RetrievalRepository retrievalRepository,
            ModelCallLogRepository modelCallLogRepository,
            EmbeddingProvider embeddingProvider,
            ChatProvider chatProvider,
            RetrievalProperties retrievalProperties,
            PromptBuilder promptBuilder,
            CitationParser citationParser
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.retrievalRepository = retrievalRepository;
        this.modelCallLogRepository = modelCallLogRepository;
        this.embeddingProvider = embeddingProvider;
        this.chatProvider = chatProvider;
        this.retrievalProperties = retrievalProperties;
        this.promptBuilder = promptBuilder;
        this.citationParser = citationParser;
    }

    public ConversationDto createConversation(long ownerId, CreateConversationRequest request) {
        knowledgeBaseRepository.findByIdAndOwner(request.kbId(), ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        String title = normalizeTitle(request.title(), "新会话");
        return toDto(conversationRepository.create(ownerId, request.kbId(), title));
    }

    public List<ConversationDto> listConversations(long ownerId, Long kbId) {
        if (kbId != null) {
            knowledgeBaseRepository.findByIdAndOwner(kbId, ownerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        }
        return conversationRepository.listByOwner(ownerId, kbId).stream()
                .map(this::toDto)
                .toList();
    }

    public ConversationDetailDto getConversation(long ownerId, long conversationId) {
        Conversation conversation = findConversation(ownerId, conversationId);
        List<RagMessage> messages = messageRepository.findByConversationId(conversation.id());
        Map<Long, List<Citation>> citationsByMessage = citationRepository.findByMessageIds(
                messages.stream().map(RagMessage::id).toList()
        ).stream().collect(Collectors.groupingBy(Citation::messageId));
        return new ConversationDetailDto(
                toDto(conversation),
                messages.stream()
                        .map(message -> toDto(message, citationsByMessage.getOrDefault(message.id(), List.of())))
                        .toList()
        );
    }

    public RagAnswerDto ask(long ownerId, long conversationId, SendMessageRequest request) {
        long start = System.nanoTime();
        Conversation conversation = findConversation(ownerId, conversationId);
        String question = normalizeQuestion(request.question());
        if ("新会话".equals(conversation.title())) {
            conversationRepository.updateTitle(conversation.id(), normalizeTitle(question, question));
        }
        List<RagHistoryMessage> history = messageRepository.findRecentHistory(conversation.id(), HISTORY_LIMIT);
        RagMessage userMessage = messageRepository.create(conversation.id(), "USER", question, "OK", null, null, null);
        List<RetrievalHit> hits = retrieve(conversation.kbId(), question);

        if (hits.isEmpty() || !hasGroundedVectorHit(hits)) {
            long latencyMs = elapsedMillis(start);
            RagMessage assistantMessage = messageRepository.create(
                    conversation.id(),
                    "ASSISTANT",
                    RagConstants.NO_ANSWER,
                    "NO_ANSWER",
                    null,
                    null,
                    latencyMs
            );
            return new RagAnswerDto(
                    userMessage.id(),
                    assistantMessage.id(),
                    RagConstants.NO_ANSWER,
                    "NO_ANSWER",
                    List.of(),
                    null,
                    latencyMs
            );
        }

        PromptBuildResult prompt = promptBuilder.build(question, hits, history);
        ChatResult chatResult;
        long chatStart = System.nanoTime();
        try {
            chatResult = chatWithRetry(prompt);
        } catch (RuntimeException ex) {
            long latencyMs = elapsedMillis(start);
            RagMessage assistantMessage = messageRepository.create(
                    conversation.id(),
                    "ASSISTANT",
                    "LLM 调用失败: " + readableMessage(ex),
                    "ERROR",
                    prompt.promptTokens(),
                    0,
                    latencyMs
            );
            modelCallLogRepository.recordChat(
                    assistantMessage.id(),
                    chatProvider.providerName(),
                    chatProvider.modelName(),
                    prompt.promptTokens(),
                    0,
                    elapsedMillis(chatStart),
                    "ERROR",
                    readableMessage(ex)
            );
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, readableMessage(ex));
        }

        CitationParseResult citations = citationParser.parse(chatResult.content(), prompt.contexts());
        String status = answerStatus(chatResult.content(), citations);
        long latencyMs = elapsedMillis(start);
        RagMessage assistantMessage = messageRepository.create(
                conversation.id(),
                "ASSISTANT",
                chatResult.content(),
                status,
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                latencyMs
        );
        citationRepository.insertAll(assistantMessage.id(), citations.citations());
        modelCallLogRepository.recordChat(
                assistantMessage.id(),
                chatProvider.providerName(),
                chatProvider.modelName(),
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                elapsedMillis(chatStart),
                "OK",
                null
        );
        return new RagAnswerDto(
                userMessage.id(),
                assistantMessage.id(),
                chatResult.content(),
                status,
                citations.citations().stream().map(this::toDto).toList(),
                citations.warning(),
                latencyMs
        );
    }

    private Conversation findConversation(long ownerId, long conversationId) {
        return conversationRepository.findByIdAndOwner(conversationId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    }

    private List<RetrievalHit> retrieve(long kbId, String question) {
        long start = System.nanoTime();
        int promptTokens = estimateTokens(question);
        try {
            List<float[]> embeddings = embeddingProvider.embed(List.of(question));
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
            return retrievalRepository.search(kbId, embeddings.get(0), question, RETRIEVAL_TOP_K);
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

    private String answerStatus(String answer, CitationParseResult citations) {
        if (RagConstants.NO_ANSWER.equals(answer == null ? "" : answer.strip())) {
            return "NO_ANSWER";
        }
        if (citations.citations().isEmpty()) {
            return "UNGROUNDED";
        }
        return "OK";
    }

    private ConversationDto toDto(Conversation conversation) {
        return new ConversationDto(conversation.id(), conversation.kbId(), conversation.title(), conversation.createdAt());
    }

    private MessageDto toDto(RagMessage message, List<Citation> citations) {
        return new MessageDto(
                message.id(),
                message.role(),
                message.content(),
                message.status(),
                message.promptTokens(),
                message.completionTokens(),
                message.latencyMs(),
                message.createdAt(),
                citations.stream()
                        .sorted(Comparator.comparingInt(Citation::rank))
                        .map(this::toDto)
                        .toList()
        );
    }

    private CitationDto toDto(Citation citation) {
        return new CitationDto(
                citation.rank(),
                citation.chunkId(),
                citation.documentFilename(),
                citation.headingPath(),
                citation.snippet(),
                citation.similarity()
        );
    }

    private CitationDto toDto(CitationDraft citation) {
        return new CitationDto(
                citation.rank(),
                citation.chunkId(),
                citation.documentFilename(),
                citation.headingPath(),
                citation.snippet(),
                citation.similarity()
        );
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question 不能为空");
        }
        String normalized = question.strip();
        if (normalized.length() > 2000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question 不能超过 2000 字");
        }
        return normalized;
    }

    private String normalizeTitle(String value, String fallback) {
        String title = value == null || value.isBlank() ? fallback : value.strip();
        return title.length() <= 30 ? title : title.substring(0, 30);
    }

    private int estimateTokens(String text) {
        return Math.max(1, text == null ? 1 : text.length() / 4);
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }

    private String readableMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "LLM 调用失败" : message;
    }
}
