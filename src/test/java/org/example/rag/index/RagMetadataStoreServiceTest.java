package org.example.rag.index;

import org.example.rag.model.IndexStatus;
import org.example.rag.model.RagChunk;
import org.example.rag.model.RagDocument;
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

class RagMetadataStoreServiceTest {

    private EmbeddedDatabase database;
    private RagMetadataStoreService metadataStoreService;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("rag-metadata-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);
        metadataStoreService = new RagMetadataStoreService(new JdbcTemplate(database));
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void storesDocumentAndChunks() {
        RagDocument document = new RagDocument();
        document.setDocumentId("doc-1");
        document.setNamespace("ops");
        document.setFileName("runbook.md");
        document.setFileHash("hash");
        document.setFileType("md");
        document.setSourcePath("rag-data/uploads/doc-1.md");
        document.setMarkdownPath("rag-data/parsed-md/doc-1.md");
        document.setParserName("markdown-file");
        document.setStatus(IndexStatus.COMPLETED);
        document.setChunkCount(1);

        RagChunk chunk = new RagChunk();
        chunk.setChunkId("chunk-1");
        chunk.setDocumentId("doc-1");
        chunk.setNamespace("ops");
        chunk.setFileName("runbook.md");
        chunk.setHeadingPath("Runbook");
        chunk.setChunkIndex(0);
        chunk.setTokenCount(5);
        chunk.setContent("content");
        chunk.setEmbeddingContent("embedding content");
        chunk.setMetadata(Map.of("parent", false, "fileType", "md"));

        metadataStoreService.upsertDocument(document);
        metadataStoreService.replaceChunks("doc-1", List.of(chunk));

        assertThat(metadataStoreService.findDocument("doc-1")).isPresent();
        assertThat(metadataStoreService.findChunks("doc-1")).hasSize(1);
        assertThat(metadataStoreService.findChunks("doc-1").get(0).getMetadata()).containsEntry("fileType", "md");
    }
}
