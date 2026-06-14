package com.ragdocs.ingestion;

import com.ragdocs.config.AiProperties;
import com.ragdocs.domain.ChunkEmbeddingInput;
import com.ragdocs.domain.Document;
import com.ragdocs.domain.IngestionJob;
import com.ragdocs.provider.EmbeddingProvider;
import com.ragdocs.repository.DocumentChunkRepository;
import com.ragdocs.repository.DocumentRepository;
import com.ragdocs.repository.IngestionJobRepository;
import com.ragdocs.repository.ModelCallLogRepository;
import com.ragdocs.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
    private static final int VECTOR_DIMENSIONS = 1024;
    private static final int EMBED_MAX_ATTEMPTS = 3;

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ModelCallLogRepository modelCallLogRepository;
    private final StorageService storageService;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingProvider embeddingProvider;
    private final AiProperties aiProperties;

    public IngestionWorker(
            DocumentRepository documentRepository,
            IngestionJobRepository ingestionJobRepository,
            DocumentChunkRepository chunkRepository,
            ModelCallLogRepository modelCallLogRepository,
            StorageService storageService,
            DocumentParser documentParser,
            DocumentChunker documentChunker,
            EmbeddingProvider embeddingProvider,
            AiProperties aiProperties
    ) {
        this.documentRepository = documentRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.chunkRepository = chunkRepository;
        this.modelCallLogRepository = modelCallLogRepository;
        this.storageService = storageService;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.embeddingProvider = embeddingProvider;
        this.aiProperties = aiProperties;
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

            IngestionJob embedJob = ingestionJobRepository.createJob(documentId, "EMBED");
            currentJobId = embedJob.id();
            ingestionJobRepository.markRunning(embedJob.id());
            documentRepository.updateStatus(documentId, "EMBEDDING", null);
            List<ChunkEmbeddingInput> embeddingInputs = chunkRepository.findEmbeddingInputs(documentId);
            embedChunks(documentId, embeddingInputs);
            long missingEmbeddings = chunkRepository.countMissingEmbeddings(documentId);
            if (missingEmbeddings > 0) {
                throw new IngestionException("存在未写入向量的 chunk: " + missingEmbeddings);
            }
            documentRepository.markReady(documentId, embeddingInputs.size());
            ingestionJobRepository.markSucceeded(embedJob.id());
        } catch (Exception ex) {
            String message = readableMessage(ex);
            log.warn("Document ingestion failed: documentId={}, message={}", documentId, message, ex);
            if (currentJobId != null) {
                ingestionJobRepository.markFailed(currentJobId, message);
            }
            documentRepository.updateStatus(documentId, "FAILED", truncate(message));
        }
    }

    private void embedChunks(long documentId, List<ChunkEmbeddingInput> chunks) {
        if (chunks.isEmpty()) {
            throw new IngestionException("没有可向量化的 chunk");
        }
        if (embeddingProvider.dimensions() != VECTOR_DIMENSIONS) {
            throw new IngestionException("Embedding 维度不匹配: expected="
                    + VECTOR_DIMENSIONS + ", actual=" + embeddingProvider.dimensions());
        }
        int batchSize = Math.min(aiProperties.embedding().maxBatchSize(), 32);
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<ChunkEmbeddingInput> batch = new ArrayList<>(chunks.subList(start, end));
            List<String> texts = batch.stream().map(ChunkEmbeddingInput::content).toList();
            List<float[]> embeddings = embedBatchWithRetry(documentId, texts);
            if (embeddings.size() != batch.size()) {
                throw new IngestionException("Embedding 响应数量不匹配: expected="
                        + batch.size() + ", actual=" + embeddings.size());
            }
            for (float[] embedding : embeddings) {
                validateEmbedding(embedding);
            }
            chunkRepository.updateEmbeddings(batch, embeddings, embeddingProvider.modelName());
        }
    }

    private List<float[]> embedBatchWithRetry(long documentId, List<String> texts) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= EMBED_MAX_ATTEMPTS; attempt++) {
            long start = System.nanoTime();
            try {
                List<float[]> embeddings = embeddingProvider.embed(texts);
                modelCallLogRepository.recordEmbedding(
                        documentId,
                        embeddingProvider.providerName(),
                        embeddingProvider.modelName(),
                        estimateTokens(texts),
                        elapsedMillis(start),
                        "OK",
                        null
                );
                return embeddings;
            } catch (RuntimeException ex) {
                last = ex;
                modelCallLogRepository.recordEmbedding(
                        documentId,
                        embeddingProvider.providerName(),
                        embeddingProvider.modelName(),
                        estimateTokens(texts),
                        elapsedMillis(start),
                        "ERROR",
                        ex.getMessage()
                );
                if (attempt < EMBED_MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new IngestionException("Embedding 失败: " + readableMessage(last), last);
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding.length != VECTOR_DIMENSIONS) {
            throw new IngestionException("Embedding 维度不匹配: expected="
                    + VECTOR_DIMENSIONS + ", actual=" + embedding.length);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(200L * (1L << (attempt - 1)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IngestionException("Embedding 重试被中断", ex);
        }
    }

    private int estimateTokens(List<String> texts) {
        int chars = texts.stream().mapToInt(text -> text == null ? 0 : text.length()).sum();
        return Math.max(1, chars / 4);
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }

    private String readableMessage(Exception ex) {
        if (ex == null) {
            return "文档处理失败";
        }
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
