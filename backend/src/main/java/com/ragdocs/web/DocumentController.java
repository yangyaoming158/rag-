package com.ragdocs.web;

import com.ragdocs.auth.CurrentUser;
import com.ragdocs.auth.JwtAuthenticationFilter;
import com.ragdocs.common.ApiResponse;
import com.ragdocs.service.DocumentService;
import com.ragdocs.web.dto.DocumentDto;
import com.ragdocs.web.dto.JobDto;
import com.ragdocs.web.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(path = "/kbs/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentDto> upload(
            @PathVariable long kbId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(documentService.upload(currentUser(request).id(), kbId, file));
    }

    @GetMapping("/kbs/{kbId}/documents")
    public ApiResponse<PageResponse<DocumentDto>> list(
            @PathVariable long kbId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(documentService.list(currentUser(request).id(), kbId, status, page, size));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable long id, HttpServletRequest request) {
        documentService.delete(currentUser(request).id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/documents/{id}/ingestion")
    public ApiResponse<List<JobDto>> ingestion(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(documentService.ingestionJobs(currentUser(request).id(), id));
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(JwtAuthenticationFilter.CURRENT_USER_ATTRIBUTE);
    }
}
