package com.ragdocs.retrieval;

import com.ragdocs.common.BusinessException;
import com.ragdocs.config.RetrievalProperties;
import com.ragdocs.domain.KnowledgeBase;
import com.ragdocs.provider.MockEmbeddingProvider;
import com.ragdocs.repository.KnowledgeBaseRepository;
import com.ragdocs.repository.ModelCallLogRepository;
import com.ragdocs.repository.RetrievalRepository;
import com.ragdocs.web.dto.RetrievalDebugRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalServiceTest {

    @Test
    void searchesOnlyAfterOwnerCheckAndPassesKbIdToRepository() {
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository(Optional.of(kb(10L, 7L)));
        FakeRetrievalRepository retrievalRepository = new FakeRetrievalRepository(List.of(
                new RetrievalHit(1L, 2L, "architecture.md", 0, "order service status machine",
                        "Architecture", null, null, 280, 0.71)
        ));
        RetrievalService service = service(knowledgeBaseRepository, retrievalRepository);

        var response = service.debug(7L, 10L, new RetrievalDebugRequest("order status", 8));

        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).documentFilename()).isEqualTo("architecture.md");
        assertThat(response.hits().get(0).aboveThreshold()).isTrue();
        assertThat(retrievalRepository.lastKbId).isEqualTo(10L);
        assertThat(retrievalRepository.lastTopK).isEqualTo(8);
        assertThat(knowledgeBaseRepository.lastOwnerId).isEqualTo(7L);
    }

    @Test
    void rejectsKbThatDoesNotBelongToOwnerBeforeSearch() {
        FakeKnowledgeBaseRepository knowledgeBaseRepository = new FakeKnowledgeBaseRepository(Optional.empty());
        FakeRetrievalRepository retrievalRepository = new FakeRetrievalRepository(List.of());
        RetrievalService service = service(knowledgeBaseRepository, retrievalRepository);

        assertThatThrownBy(() -> service.debug(7L, 10L, new RetrievalDebugRequest("order status", 8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库不存在");
        assertThat(retrievalRepository.searchCalled).isFalse();
    }

    private RetrievalService service(KnowledgeBaseRepository knowledgeBaseRepository, RetrievalRepository retrievalRepository) {
        return new RetrievalService(
                knowledgeBaseRepository,
                retrievalRepository,
                new MockEmbeddingProvider(1024, "mock-bge-m3"),
                new NoopModelCallLogRepository(),
                new RetrievalProperties(0.4, 8, 20)
        );
    }

    private KnowledgeBase kb(long id, long ownerId) {
        return new KnowledgeBase(id, ownerId, "kb", null, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private static class FakeKnowledgeBaseRepository extends KnowledgeBaseRepository {
        private final Optional<KnowledgeBase> result;
        private long lastOwnerId;

        FakeKnowledgeBaseRepository(Optional<KnowledgeBase> result) {
            super(null);
            this.result = result;
        }

        @Override
        public Optional<KnowledgeBase> findByIdAndOwner(long id, long ownerId) {
            lastOwnerId = ownerId;
            return result.filter(kb -> kb.id() == id && kb.ownerId() == ownerId);
        }
    }

    private static class FakeRetrievalRepository extends RetrievalRepository {
        private final List<RetrievalHit> result;
        private boolean searchCalled;
        private long lastKbId;
        private int lastTopK;

        FakeRetrievalRepository(List<RetrievalHit> result) {
            super(null);
            this.result = result;
        }

        @Override
        public List<RetrievalHit> search(long kbId, float[] queryVector, int topK) {
            searchCalled = true;
            lastKbId = kbId;
            lastTopK = topK;
            return result;
        }
    }

    private static class NoopModelCallLogRepository extends ModelCallLogRepository {
        NoopModelCallLogRepository() {
            super(null);
        }

        @Override
        public void recordEmbedding(Long documentId, String provider, String model, int promptTokens, long latencyMs, String status, String errorMessage) {
        }
    }
}
