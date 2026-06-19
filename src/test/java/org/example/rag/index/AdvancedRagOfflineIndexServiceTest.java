package org.example.rag.index;

import org.example.rag.chunk.ChunkEnrichService;
import org.example.rag.chunk.MarkdownChunkService;
import org.example.rag.chunk.ParentChildChunkService;
import org.example.rag.chunk.TokenEstimator;
import org.example.rag.config.RagProperties;
import org.example.rag.markdown.MarkdownCleaner;
import org.example.rag.markdown.MarkdownNormalizeService;
import org.example.rag.markdown.MarkdownSectionParser;
import org.example.rag.model.IndexResult;
import org.example.rag.model.IndexStatus;
import org.example.rag.model.RagChunk;
import org.example.rag.parser.DocumentParserService;
import org.example.rag.parser.FileTypeDetector;
import org.example.rag.parser.MarkdownFileParser;
import org.example.rag.parser.TikaFallbackParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvancedRagOfflineIndexServiceTest {

    @TempDir
    Path tempDir;

    private EmbeddedDatabase database;
    private RagEmbeddingService embeddingService;
    private MilvusRagIndexService milvusRagIndexService;
    private AdvancedRagOfflineIndexService offlineIndexService;
    private RagMetadataStoreService metadataStoreService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("rag-offline-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        RagProperties properties = new RagProperties();
        properties.getStorage().setUploadDir(tempDir.resolve("uploads").toString());
        properties.getStorage().setMarkdownDir(tempDir.resolve("parsed-md").toString());
        properties.getStorage().setChunkDir(tempDir.resolve("chunks").toString());
        properties.getStorage().setLogDir(tempDir.resolve("logs").toString());
        properties.getChunk().setTargetTokens(40);
        properties.getChunk().setMinTokens(1);

        FileTypeDetector detector = new FileTypeDetector();
        TokenEstimator tokenEstimator = new TokenEstimator();
        metadataStoreService = new RagMetadataStoreService(new JdbcTemplate(database));
        embeddingService = mock(RagEmbeddingService.class);
        milvusRagIndexService = mock(MilvusRagIndexService.class);
        doNothing().when(milvusRagIndexService).deleteByDocumentId(any());
        when(embeddingService.embedChunks(any())).thenAnswer(invocation -> {
            List<RagChunk> chunks = invocation.getArgument(0);
            Map<String, List<Float>> vectors = new LinkedHashMap<>();
            for (RagChunk chunk : chunks) {
                if (!Boolean.TRUE.equals(chunk.getParent())) {
                    vectors.put(chunk.getChunkId(), vector());
                }
            }
            return vectors;
        });

        offlineIndexService = new AdvancedRagOfflineIndexService(
                properties,
                detector,
                new DocumentParserService(
                        List.of(new MarkdownFileParser(detector), new TikaFallbackParser(detector)),
                        properties,
                        detector),
                new MarkdownNormalizeService(new MarkdownCleaner()),
                new MarkdownChunkService(new MarkdownSectionParser(tokenEstimator), tokenEstimator, properties),
                new ParentChildChunkService(properties, tokenEstimator),
                new ChunkEnrichService(properties),
                embeddingService,
                milvusRagIndexService,
                metadataStoreService);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void indexesMarkdownFileThroughOfflinePipeline() throws Exception {
        Path file = tempDir.resolve("runbook.md");
        Files.writeString(file, """
                # SuperBizAgent Runbook
                ## Redis Timeout
                ERROR order-service traceId=abc123456789 ERR_TIMEOUT Redis CPU_HIGH
                Restart order-service and check Redis connection pool.
                """);

        IndexResult result = offlineIndexService.indexFile(file, "ops");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(IndexStatus.COMPLETED);
        assertThat(Files.exists(Path.of(result.getMarkdownPath()))).isTrue();
        assertThat(Files.exists(Path.of(result.getChunkPath()))).isTrue();
        assertThat(metadataStoreService.findDocument(result.getDocumentId())).isPresent();
        assertThat(metadataStoreService.findChunks(result.getDocumentId())).isNotEmpty();

        ArgumentCaptor<List<RagChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingService).embedChunks(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).anySatisfy(chunk -> {
            if (!Boolean.TRUE.equals(chunk.getParent())) {
                assertThat(chunk.getEmbeddingContent()).contains("文档：runbook.md");
                assertThat(chunk.getMetadata()).containsEntry("serviceName", "order-service");
                assertThat(chunk.getMetadata()).containsEntry("component", "Redis");
            }
        });
        verify(milvusRagIndexService).upsertChunks(eq(result.getDocumentId()), any(), any());
    }

    private List<Float> vector() {
        return java.util.stream.IntStream.range(0, 1024)
                .mapToObj(i -> 0.01f)
                .toList();
    }
}
