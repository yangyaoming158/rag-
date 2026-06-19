package com.ragdocs.rag;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.domain.Citation;
import com.ragdocs.domain.QaFeedback;
import com.ragdocs.repository.CitationRepository;
import com.ragdocs.repository.MessageRepository;
import com.ragdocs.repository.QaFeedbackRepository;
import com.ragdocs.web.dto.AdminQaFeedbackDto;
import com.ragdocs.web.dto.CitationDto;
import com.ragdocs.web.dto.CreateQaFeedbackRequest;
import com.ragdocs.web.dto.PageResponse;
import com.ragdocs.web.dto.QaFeedbackDto;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QaFeedbackService {
    private static final Set<String> RATINGS = Set.of(
            "HELPFUL",
            "WRONG",
            "CITATION_IRRELEVANT",
            "SHOULD_HAVE_ANSWERED",
            "SHOULD_HAVE_REFUSED",
            "TOO_LONG",
            "TOO_SHORT"
    );
    private static final Set<String> LOW_QUALITY_RATINGS = Set.of(
            "WRONG",
            "CITATION_IRRELEVANT",
            "SHOULD_HAVE_ANSWERED",
            "SHOULD_HAVE_REFUSED",
            "TOO_LONG",
            "TOO_SHORT"
    );

    private final MessageRepository messageRepository;
    private final QaFeedbackRepository qaFeedbackRepository;
    private final CitationRepository citationRepository;

    public QaFeedbackService(
            MessageRepository messageRepository,
            QaFeedbackRepository qaFeedbackRepository,
            CitationRepository citationRepository
    ) {
        this.messageRepository = messageRepository;
        this.qaFeedbackRepository = qaFeedbackRepository;
        this.citationRepository = citationRepository;
    }

    public QaFeedbackDto submit(long ownerId, long conversationId, long messageId, CreateQaFeedbackRequest request) {
        messageRepository.findAssistantByOwnerAndConversation(ownerId, conversationId, messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "助手回答不存在"));
        QaFeedback feedback = qaFeedbackRepository.upsert(
                messageId,
                ownerId,
                normalizeRequiredRating(request.rating(), RATINGS, "反馈类型不合法"),
                normalizeText(request.reason(), 120),
                normalizeText(request.comment(), 1000)
        );
        return toDto(feedback);
    }

    public PageResponse<AdminQaFeedbackDto> listLowQuality(String rating, int page, int size) {
        String normalizedRating = normalizeRating(rating, LOW_QUALITY_RATINGS, "反馈类型不合法");
        List<QaFeedbackRepository.AdminQaFeedbackRow> rows =
                qaFeedbackRepository.findAdminLowQualityFeedback(normalizedRating, page, size);
        List<Long> messageIds = rows.stream().map(QaFeedbackRepository.AdminQaFeedbackRow::messageId).toList();
        Map<Long, List<Citation>> citationsByMessage = citationRepository.findByMessageIds(messageIds)
                .stream()
                .collect(Collectors.groupingBy(Citation::messageId));
        long total = qaFeedbackRepository.countAdminLowQualityFeedback(normalizedRating);
        return PageResponse.of(
                rows.stream()
                        .map(row -> toDto(row, citationsByMessage.getOrDefault(row.messageId(), List.of())))
                        .toList(),
                page,
                size,
                total
        );
    }

    private QaFeedbackDto toDto(QaFeedback feedback) {
        return new QaFeedbackDto(
                feedback.id(),
                feedback.messageId(),
                feedback.rating(),
                feedback.reason(),
                feedback.comment(),
                feedback.createdAt()
        );
    }

    private AdminQaFeedbackDto toDto(QaFeedbackRepository.AdminQaFeedbackRow row, List<Citation> citations) {
        return new AdminQaFeedbackDto(
                row.id(),
                row.messageId(),
                row.conversationId(),
                row.kbId(),
                row.kbName(),
                row.userId(),
                row.username(),
                row.questionMessageId(),
                row.question(),
                row.answer(),
                row.answerStatus(),
                row.promptTokens(),
                row.completionTokens(),
                row.answerLatencyMs(),
                row.provider(),
                row.model(),
                row.modelLatencyMs(),
                row.rating(),
                row.reason(),
                row.comment(),
                row.createdAt(),
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

    private String normalizeRating(String rating, Set<String> allowedRatings, String message) {
        if (rating == null || rating.isBlank()) {
            return null;
        }
        String normalized = rating.trim().toUpperCase();
        if (!allowedRatings.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return normalized;
    }

    private String normalizeRequiredRating(String rating, Set<String> allowedRatings, String message) {
        String normalized = normalizeRating(rating, allowedRatings, message);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "rating 不能为空");
        }
        return normalized;
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "反馈内容过长");
        }
        return normalized;
    }
}
