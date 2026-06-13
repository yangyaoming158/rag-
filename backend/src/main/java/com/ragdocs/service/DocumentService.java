package com.ragdocs.service;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.domain.Document;
import com.ragdocs.domain.IngestionJob;
import com.ragdocs.repository.DocumentRepository;
import com.ragdocs.repository.IngestionJobRepository;
import com.ragdocs.repository.KnowledgeBaseRepository;
import com.ragdocs.web.dto.DocumentDto;
import com.ragdocs.web.dto.JobDto;
import com.ragdocs.web.dto.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class DocumentService {
    private static final Set<String> DOCUMENT_STATUSES = Set.of(
            "UPLOADED", "PARSING", "CHUNKING", "EMBEDDING", "READY", "FAILED"
    );

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentFileValidator fileValidator;
    private final StorageService storageService;

    public DocumentService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            IngestionJobRepository ingestionJobRepository,
            DocumentFileValidator fileValidator,
            StorageService storageService
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.fileValidator = fileValidator;
        this.storageService = storageService;
    }

    @Transactional
    public DocumentDto upload(long ownerId, long kbId, MultipartFile file) {
        ensureKbOwner(ownerId, kbId);
        DocumentFileValidator.ValidatedUpload upload = fileValidator.validate(file);
        String sha256 = sha256(file);
        documentRepository.findByKbIdAndSha256(kbId, sha256).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT, "重复文件，已存在 docId=" + existing.id());
        });

        Document document;
        try {
            document = documentRepository.createUploaded(
                    kbId,
                    upload.originalFilename(),
                    upload.contentType(),
                    upload.fileSize(),
                    sha256
            );
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "重复文件");
        }

        String storagePath = null;
        try (InputStream inputStream = file.getInputStream()) {
            storagePath = storageService.store(kbId, document.id(), upload.extension(), inputStream);
            documentRepository.updateStoragePath(document.id(), storagePath);
            IngestionJob job = ingestionJobRepository.createParseJob(document.id());
            return toDto(document, job.id());
        } catch (IOException ex) {
            cleanup(storagePath);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        } catch (RuntimeException ex) {
            cleanup(storagePath);
            throw ex;
        }
    }

    public PageResponse<DocumentDto> list(long ownerId, long kbId, String status, int page, int size) {
        ensureKbOwner(ownerId, kbId);
        String normalizedStatus = normalizeStatus(status);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        List<DocumentDto> documents = documentRepository.listByKb(kbId, normalizedStatus, normalizedPage, normalizedSize)
                .stream()
                .map(document -> toDto(document, null))
                .toList();
        long total = documentRepository.countByKb(kbId, normalizedStatus);
        return PageResponse.of(documents, normalizedPage, normalizedSize, total);
    }

    @Transactional
    public void delete(long ownerId, long documentId) {
        Document document = documentRepository.findByIdAndOwner(documentId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        int deleted = documentRepository.deleteByIdAndOwner(documentId, ownerId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        try {
            storageService.delete(document.storagePath());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档文件清理失败");
        }
    }

    public List<JobDto> ingestionJobs(long ownerId, long documentId) {
        Document document = documentRepository.findByIdAndOwner(documentId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        return ingestionJobRepository.findByDocumentId(document.id()).stream()
                .map(this::toDto)
                .toList();
    }

    private void ensureKbOwner(long ownerId, long kbId) {
        knowledgeBaseRepository.findByIdAndOwner(kbId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!DOCUMENT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文档状态不合法");
        }
        return normalized;
    }

    private String sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件读取失败");
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }

    private void cleanup(String storagePath) {
        try {
            storageService.delete(storagePath);
        } catch (IOException ignored) {
            // Primary failure is reported to the caller; cleanup best effort only.
        }
    }

    private DocumentDto toDto(Document document, Long jobId) {
        return new DocumentDto(
                document.id(),
                document.kbId(),
                document.originalFilename(),
                document.contentType(),
                document.fileSize(),
                document.status(),
                document.errorMessage(),
                document.chunkCount(),
                jobId,
                document.createdAt(),
                document.updatedAt()
        );
    }

    private JobDto toDto(IngestionJob job) {
        return new JobDto(
                job.id(),
                job.documentId(),
                job.phase(),
                job.status(),
                job.attempt(),
                job.maxAttempt(),
                job.errorMessage(),
                job.startedAt(),
                job.finishedAt(),
                job.createdAt()
        );
    }
}
