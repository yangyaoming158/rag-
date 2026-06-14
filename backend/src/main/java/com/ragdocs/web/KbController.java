package com.ragdocs.web;

import com.ragdocs.auth.CurrentUser;
import com.ragdocs.auth.JwtAuthenticationFilter;
import com.ragdocs.common.ApiResponse;
import com.ragdocs.retrieval.RetrievalService;
import com.ragdocs.service.KbService;
import com.ragdocs.web.dto.CreateKbRequest;
import com.ragdocs.web.dto.KbDto;
import com.ragdocs.web.dto.RetrievalDebugRequest;
import com.ragdocs.web.dto.RetrievalDebugResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kbs")
public class KbController {
    private final KbService kbService;
    private final RetrievalService retrievalService;

    public KbController(KbService kbService, RetrievalService retrievalService) {
        this.kbService = kbService;
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public ApiResponse<List<KbDto>> list(HttpServletRequest request) {
        return ApiResponse.ok(kbService.list(currentUser(request).id()));
    }

    @PostMapping
    public ApiResponse<KbDto> create(@Valid @RequestBody CreateKbRequest body, HttpServletRequest request) {
        return ApiResponse.ok(kbService.create(currentUser(request).id(), body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id, HttpServletRequest request) {
        kbService.delete(currentUser(request).id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/retrieval/debug")
    public ApiResponse<RetrievalDebugResponse> retrievalDebug(
            @PathVariable long id,
            @Valid @RequestBody RetrievalDebugRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(retrievalService.debug(currentUser(request).id(), id, body));
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(JwtAuthenticationFilter.CURRENT_USER_ATTRIBUTE);
    }
}
