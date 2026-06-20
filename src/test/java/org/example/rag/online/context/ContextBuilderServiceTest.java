package org.example.rag.online.context;

import org.example.rag.chunk.TokenEstimator;
import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.RagChunk;
import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.EvidenceContext;
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

class ContextBuilderServiceTest {

    private EmbeddedDatabase database;
    private ContextBuilderService contextBuilderService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("rag-context-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);
        RagMetadataStoreService storeService = new RagMetadataStoreService(new JdbcTemplate(database));
        storeService.replaceChunks("doc-1", List.of(parent()));
        contextBuilderService = new ContextBuilderService(
                new RagOnlineProperties(),
                new TokenEstimator(),
                new CitationService(),
                new ContextCompressorService(),
                storeService);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void buildsNumberedEvidenceAndCitationsWithParentExpansion() {
        EvidenceContext context = contextBuilderService.build("Redis 超时", List.of(candidate()));

        assertThat(context.getContextText()).contains("[1] 文件：runbook.md", "Parent Redis section with detailed troubleshooting");
        assertThat(context.getCitations()).hasSize(1);
        assertThat(context.getCitations().get(0).getChunkId()).isEqualTo("chunk-1");
        assertThat(context.getTotalTokens()).isPositive();
    }

    private RetrievalCandidate candidate() {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId("chunk-1");
        candidate.setDocumentId("doc-1");
        candidate.setParentChunkId("parent-1");
        candidate.setNamespace("default");
        candidate.setFileName("runbook.md");
        candidate.setHeadingPath("Redis > Timeout");
        candidate.setChunkIndex(0);
        candidate.setContent("short");
        candidate.setMetadata(Map.of("sourcePath", "rag-data/uploads/runbook.md"));
        return candidate;
    }

    private RagChunk parent() {
        RagChunk chunk = new RagChunk();
        chunk.setChunkId("parent-1");
        chunk.setDocumentId("doc-1");
        chunk.setNamespace("default");
        chunk.setFileName("runbook.md");
        chunk.setHeadingPath("Redis");
        chunk.setChunkIndex(-1);
        chunk.setTokenCount(100);
        chunk.setParent(true);
        chunk.setContent("Parent Redis section with detailed troubleshooting steps and log examples.");
        chunk.setEmbeddingContent(chunk.getContent());
        chunk.setMetadata(Map.of("parent", true));
        return chunk;
    }
}
