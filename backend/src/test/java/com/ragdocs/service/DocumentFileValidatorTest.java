package com.ragdocs.service;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileValidatorTest {
    private final DocumentFileValidator validator = new DocumentFileValidator();

    @Test
    void acceptsAllowedMarkdownFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.md",
                "text/markdown",
                "# Title".getBytes()
        );

        DocumentFileValidator.ValidatedUpload upload = validator.validate(file);

        assertThat(upload.originalFilename()).isEqualTo("notes.md");
        assertThat(upload.extension()).isEqualTo("md");
        assertThat(upload.contentType()).isEqualTo("text/markdown");
    }

    @Test
    void rejectsExecutableFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "run.exe",
                "application/octet-stream",
                "binary".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE);
                    assertThat(ex.getMessage()).contains("仅支持");
                });
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE));
    }

    @Test
    void rejectsOversizedFile() {
        MultipartFileStub file = new MultipartFileStub(
                "huge.pdf",
                "application/pdf",
                DocumentFileValidator.MAX_FILE_SIZE_BYTES + 1
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE);
                    assertThat(ex.getMessage()).contains("20MB");
                });
    }
}
