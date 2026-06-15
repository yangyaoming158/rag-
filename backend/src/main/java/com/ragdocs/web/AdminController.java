package com.ragdocs.web;

import com.ragdocs.auth.CurrentUser;
import com.ragdocs.auth.JwtAuthenticationFilter;
import com.ragdocs.common.ApiResponse;
import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.repository.AdminStatsRepository;
import com.ragdocs.repository.IngestionJobRepository;
import com.ragdocs.repository.ModelCallLogRepository;
import com.ragdocs.retrieval.RetrievalService;
import com.ragdocs.web.dto.AdminIngestionJobDto;
import com.ragdocs.web.dto.AdminRetrievalDebugRequest;
import com.ragdocs.web.dto.ModelCallDto;
import com.ragdocs.web.dto.PageResponse;
import com.ragdocs.web.dto.RetrievalDebugRequest;
import com.ragdocs.web.dto.RetrievalDebugResponse;
import com.ragdocs.web.dto.StatsOverviewDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Set<String> JOB_STATUSES = Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED");
    private static final Set<String> CALL_TYPES = Set.of("CHAT", "EMBEDDING");
    private static final Set<String> CALL_STATUSES = Set.of("OK", "ERROR");

    private final IngestionJobRepository ingestionJobRepository;
    private final ModelCallLogRepository modelCallLogRepository;
    private final AdminStatsRepository adminStatsRepository;
    private final RetrievalService retrievalService;

    public AdminController(
            IngestionJobRepository ingestionJobRepository,
            ModelCallLogRepository modelCallLogRepository,
            AdminStatsRepository adminStatsRepository,
            RetrievalService retrievalService
    ) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.modelCallLogRepository = modelCallLogRepository;
        this.adminStatsRepository = adminStatsRepository;
        this.retrievalService = retrievalService;
    }

    @GetMapping("/ingestion-jobs")
    public ApiResponse<PageResponse<AdminIngestionJobDto>> ingestionJobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        ensureAdmin(currentUser(request));
        String normalizedStatus = normalize(status, JOB_STATUSES, "任务状态不合法");
        Page pageSpec = normalizePage(page, size);
        List<AdminIngestionJobDto> content = ingestionJobRepository
                .findAdminJobs(normalizedStatus, pageSpec.page(), pageSpec.size())
                .stream()
                .map(this::toDto)
                .toList();
        long total = ingestionJobRepository.countAdminJobs(normalizedStatus);
        return ApiResponse.ok(PageResponse.of(content, pageSpec.page(), pageSpec.size(), total));
    }

    @GetMapping("/model-calls")
    public ApiResponse<PageResponse<ModelCallDto>> modelCalls(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        ensureAdmin(currentUser(request));
        String normalizedType = normalize(type, CALL_TYPES, "调用类型不合法");
        String normalizedStatus = normalize(status, CALL_STATUSES, "调用状态不合法");
        Page pageSpec = normalizePage(page, size);
        List<ModelCallDto> content = modelCallLogRepository
                .findAdminCalls(normalizedType, normalizedStatus, pageSpec.page(), pageSpec.size())
                .stream()
                .map(this::toDto)
                .toList();
        long total = modelCallLogRepository.countAdminCalls(normalizedType, normalizedStatus);
        return ApiResponse.ok(PageResponse.of(content, pageSpec.page(), pageSpec.size(), total));
    }

    @PostMapping("/retrieval-debug")
    public ApiResponse<RetrievalDebugResponse> retrievalDebug(
            @Valid @RequestBody AdminRetrievalDebugRequest body,
            HttpServletRequest request
    ) {
        CurrentUser currentUser = currentUser(request);
        ensureAdmin(currentUser);
        return ApiResponse.ok(retrievalService.debug(
                currentUser.id(),
                body.kbId(),
                new RetrievalDebugRequest(body.query(), body.topK())
        ));
    }

    @GetMapping("/stats/overview")
    public ApiResponse<StatsOverviewDto> statsOverview(HttpServletRequest request) {
        ensureAdmin(currentUser(request));
        AdminStatsRepository.OverviewStats stats = adminStatsRepository.overview();
        return ApiResponse.ok(new StatsOverviewDto(
                stats.kbCount(),
                stats.docCount(),
                stats.chunkCount(),
                stats.tokenSum(),
                stats.avgLatencyMs()
        ));
    }

    private void ensureAdmin(CurrentUser currentUser) {
        if (currentUser == null || !"ADMIN".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private String normalize(String value, Set<String> allowedValues, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return normalized;
    }

    private Page normalizePage(int page, int size) {
        return new Page(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(JwtAuthenticationFilter.CURRENT_USER_ATTRIBUTE);
    }

    private AdminIngestionJobDto toDto(IngestionJobRepository.AdminIngestionJobRow row) {
        return new AdminIngestionJobDto(
                row.id(),
                row.documentId(),
                row.documentFilename(),
                row.documentStatus(),
                row.kbId(),
                row.kbName(),
                row.phase(),
                row.status(),
                row.attempt(),
                row.maxAttempt(),
                row.errorMessage(),
                row.startedAt(),
                row.finishedAt(),
                row.createdAt()
        );
    }

    private ModelCallDto toDto(ModelCallLogRepository.ModelCallLogRow row) {
        return new ModelCallDto(
                row.id(),
                row.callType(),
                row.provider(),
                row.model(),
                row.messageId(),
                row.documentId(),
                row.documentFilename(),
                row.promptTokens(),
                row.completionTokens(),
                row.latencyMs(),
                row.status(),
                row.errorMessage(),
                row.createdAt()
        );
    }

    private record Page(int page, int size) {
    }
}
