package com.ragdocs.service;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    String store(long kbId, long documentId, String extension, InputStream inputStream) throws IOException;

    void delete(String storagePath) throws IOException;

    void deleteKnowledgeBase(long kbId) throws IOException;
}
