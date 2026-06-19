package com.ragdocs.web;

import com.ragdocs.auth.CurrentUser;
import com.ragdocs.auth.JwtAuthenticationFilter;
import com.ragdocs.common.ApiResponse;
import com.ragdocs.rag.QaFeedbackService;
import com.ragdocs.rag.RagService;
import com.ragdocs.web.dto.ConversationDetailDto;
import com.ragdocs.web.dto.ConversationDto;
import com.ragdocs.web.dto.CreateConversationRequest;
import com.ragdocs.web.dto.CreateQaFeedbackRequest;
import com.ragdocs.web.dto.QaFeedbackDto;
import com.ragdocs.web.dto.RagAnswerDto;
import com.ragdocs.web.dto.SendMessageRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final RagService ragService;
    private final QaFeedbackService qaFeedbackService;

    public ConversationController(RagService ragService, QaFeedbackService qaFeedbackService) {
        this.ragService = ragService;
        this.qaFeedbackService = qaFeedbackService;
    }

    @PostMapping
    public ApiResponse<ConversationDto> create(@Valid @RequestBody CreateConversationRequest body, HttpServletRequest request) {
        return ApiResponse.ok(ragService.createConversation(currentUser(request).id(), body));
    }

    @GetMapping
    public ApiResponse<List<ConversationDto>> list(@RequestParam(required = false) Long kbId, HttpServletRequest request) {
        return ApiResponse.ok(ragService.listConversations(currentUser(request).id(), kbId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDetailDto> get(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(ragService.getConversation(currentUser(request).id(), id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<RagAnswerDto> ask(
            @PathVariable long id,
            @Valid @RequestBody SendMessageRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(ragService.ask(currentUser(request).id(), id, body));
    }

    @PostMapping("/{id}/messages/{messageId}/feedback")
    public ApiResponse<QaFeedbackDto> feedback(
            @PathVariable long id,
            @PathVariable long messageId,
            @Valid @RequestBody CreateQaFeedbackRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(qaFeedbackService.submit(currentUser(request).id(), id, messageId, body));
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(JwtAuthenticationFilter.CURRENT_USER_ATTRIBUTE);
    }
}
