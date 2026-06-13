package com.ragdocs.service;

import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class LocalStorageService implements StorageService {
    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
    }

    @Override
    public String store(long kbId, long documentId, String extension, InputStream inputStream) throws IOException {
        String storagePath = kbId + "/" + documentId + "." + extension;
        Path target = resolve(storagePath);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return storagePath;
    }

    @Override
    public void delete(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        Files.deleteIfExists(resolve(storagePath));
    }

    @Override
    public void deleteKnowledgeBase(long kbId) throws IOException {
        Path kbDir = resolve(Long.toString(kbId));
        if (!Files.exists(kbDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(kbDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private Path resolve(String storagePath) {
        Path target = root.resolve(storagePath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法存储路径");
        }
        return target;
    }
}
