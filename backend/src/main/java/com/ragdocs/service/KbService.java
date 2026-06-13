package com.ragdocs.service;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.domain.KnowledgeBase;
import com.ragdocs.repository.KnowledgeBaseRepository;
import com.ragdocs.web.dto.CreateKbRequest;
import com.ragdocs.web.dto.KbDto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
public class KbService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final StorageService storageService;

    public KbService(KnowledgeBaseRepository knowledgeBaseRepository, StorageService storageService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.storageService = storageService;
    }

    public List<KbDto> list(long ownerId) {
        return knowledgeBaseRepository.listByOwner(ownerId).stream()
                .map(row -> toDto(row.knowledgeBase(), row.documentCount()))
                .toList();
    }

    public KbDto create(long ownerId, CreateKbRequest request) {
        String name = request.name().trim();
        String description = normalizeDescription(request.description());
        try {
            return toDto(knowledgeBaseRepository.create(ownerId, name, description), 0);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "同名知识库已存在");
        }
    }

    @Transactional
    public void delete(long ownerId, long kbId) {
        knowledgeBaseRepository.findByIdAndOwner(kbId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
        int deleted = knowledgeBaseRepository.deleteByIdAndOwner(kbId, ownerId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        try {
            storageService.deleteKnowledgeBase(kbId);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "知识库文件清理失败");
        }
    }

    private KbDto toDto(KnowledgeBase kb, long documentCount) {
        return new KbDto(kb.id(), kb.name(), kb.description(), documentCount, kb.createdAt(), kb.updatedAt());
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
