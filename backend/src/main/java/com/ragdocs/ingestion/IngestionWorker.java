package com.ragdocs.ingestion;

import com.ragdocs.domain.Document;
import com.ragdocs.domain.IngestionJob;
import com.ragdocs.repository.DocumentChunkRepository;
import com.ragdocs.repository.DocumentRepository;
import com.ragdocs.repository.IngestionJobRepository;
import com.ragdocs.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StorageService storageService;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;

    public IngestionWorker(
            DocumentRepository documentRepository,
            IngestionJobRepository ingestionJobRepository,
            DocumentChunkRepository chunkRepository,
            StorageService storageService,
            DocumentParser documentParser,
            DocumentChunker documentChunker
    ) {
        this.documentRepository = documentRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
    }

    @Async("ingestionExecutor")
    public void process(long documentId) {
        Long currentJobId = null;
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IngestionException("文档不存在"));

            IngestionJob parseJob = ingestionJobRepository.findLatestPending(documentId, "PARSE")
                    .orElseGet(() -> ingestionJobRepository.createJob(documentId, "PARSE"));
            currentJobId = parseJob.id();
            ingestionJobRepository.markRunning(parseJob.id());
            documentRepository.updateStatus(documentId, "PARSING", null);

            ParsedDocument parsedDocument;
            try (InputStream inputStream = storageService.load(document.storagePath())) {
                parsedDocument = documentParser.parse(document, inputStream);
            }
            ingestionJobRepository.markSucceeded(parseJob.id());

            IngestionJob chunkJob = ingestionJobRepository.createJob(documentId, "CHUNK");
            currentJobId = chunkJob.id();
            ingestionJobRepository.markRunning(chunkJob.id());
            documentRepository.updateStatus(documentId, "CHUNKING", null);

            List<ChunkDraft> chunks = documentChunker.chunk(parsedDocument);
            chunkRepository.deleteByDocumentId(documentId);
            chunkRepository.insertAll(documentId, document.kbId(), chunks);
            documentRepository.markChunked(documentId, chunks.size());
            ingestionJobRepository.markSucceeded(chunkJob.id());
        } catch (Exception ex) {
            String message = readableMessage(ex);
            log.warn("Document ingestion failed: documentId={}, message={}", documentId, message, ex);
            if (currentJobId != null) {
                ingestionJobRepository.markFailed(currentJobId, message);
            }
            documentRepository.updateStatus(documentId, "FAILED", truncate(message));
        }
    }

    private String readableMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "文档处理失败";
        }
        return message;
    }

    private String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
