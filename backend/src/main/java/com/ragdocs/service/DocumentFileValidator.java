package com.ragdocs.service;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DocumentFileValidator {
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private static final Map<String, String> DEFAULT_CONTENT_TYPES = Map.of(
            "md", "text/markdown",
            "markdown", "text/markdown",
            "txt", "text/plain",
            "pdf", "application/pdf"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = DEFAULT_CONTENT_TYPES.keySet();

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE, "文件不能超过 20MB");
        }

        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE, "仅支持 md、markdown、txt、pdf 文件");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = DEFAULT_CONTENT_TYPES.get(extension);
        }
        return new ValidatedUpload(originalFilename, extension, contentType, file.getSize());
    }

    private String safeOriginalFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE, "文件名不能为空");
        }
        String normalized = rawFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE, "文件名不能为空");
        }
        return filename;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ValidatedUpload(String originalFilename, String extension, String contentType, long fileSize) {
    }
}
