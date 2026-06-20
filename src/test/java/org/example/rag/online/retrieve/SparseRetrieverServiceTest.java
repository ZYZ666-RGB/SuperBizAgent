package org.example.rag.online.retrieve;

import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.RagChunk;
import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.RetrievalCandidate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SparseRetrieverServiceTest {

    private EmbeddedDatabase database;
    private SparseRetrieverService sparseRetrieverService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("rag-sparse-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);
        RagMetadataStoreService storeService = new RagMetadataStoreService(new JdbcTemplate(database));
        storeService.replaceChunks("doc-1", List.of(
                chunk("chunk-1", "Redis Timeout", "Redis timeout ERR_TIMEOUT order-service traceId abc", Map.of("component", "Redis", "serviceName", "order-service")),
                chunk("chunk-2", "Milvus Index", "Milvus indexing markdown chunks", Map.of("component", "Milvus")),
                parentChunk("parent-1")
        ));
        sparseRetrieverService = new SparseRetrieverService(storeService);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void searchesChunksWithNamespaceAndMetadataFilters() {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.getComponents().add("Redis");
        analysis.getServiceNames().add("order-service");

        List<RetrievalCandidate> candidates = sparseRetrieverService.search(
                "Redis 超时 order-service",
                "default",
                Map.of("component", "Redis"),
                5,
                analysis);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getChunkId()).isEqualTo("chunk-1");
        assertThat(candidates.get(0).getSparseScore()).isGreaterThan(0.0);
    }

    private RagChunk chunk(String chunkId, String heading, String content, Map<String, Object> metadata) {
        RagChunk chunk = new RagChunk();
        chunk.setChunkId(chunkId);
        chunk.setDocumentId("doc-1");
        chunk.setNamespace("default");
        chunk.setFileName("runbook.md");
        chunk.setHeadingPath(heading);
        chunk.setChunkIndex(chunkId.endsWith("1") ? 0 : 1);
        chunk.setTokenCount(30);
        chunk.setContent(content);
        chunk.setEmbeddingContent(content);
        chunk.setMetadata(metadata);
        return chunk;
    }

    private RagChunk parentChunk(String chunkId) {
        RagChunk parent = chunk(chunkId, "Parent", "Parent Redis section", Map.of("parent", true));
        parent.setChunkIndex(-1);
        parent.setParent(true);
        return parent;
    }
}
