package org.example.rag.chunk;

import org.example.rag.config.RagProperties;
import org.example.rag.model.ParsedDocument;
import org.example.rag.model.RagChunk;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkEnrichServiceTest {

    @Test
    void buildsEmbeddingContentAndAiOpsMetadata() {
        ChunkEnrichService service = new ChunkEnrichService(new RagProperties());
        ParsedDocument document = new ParsedDocument();
        document.setDocumentId("doc-1");
        document.setNamespace("ops");
        document.setFileName("runbook.md");
        document.setFileType("md");
        document.setSourcePath(Path.of("runbook.md"));
        document.setParserName("markdown-file");
        RagChunk chunk = new RagChunk();
        chunk.setChunkId("chunk-1");
        chunk.setHeadingPath("Runbook > Alert");
        chunk.setContent("ERROR order-service traceId=abc123456789 ERR_TIMEOUT Redis CPU_HIGH");
        chunk.setChunkIndex(0);
        chunk.setTokenCount(8);

        service.enrich(List.of(chunk), document);

        assertThat(chunk.getEmbeddingContent()).contains("文档：runbook.md");
        assertThat(chunk.getEmbeddingContent()).contains("章节：Runbook > Alert");
        assertThat(chunk.getMetadata()).containsEntry("serviceName", "order-service");
        assertThat(chunk.getMetadata()).containsEntry("errorCode", "ERR_TIMEOUT");
        assertThat(chunk.getMetadata()).containsEntry("component", "Redis");
        assertThat(chunk.getMetadata()).containsEntry("alertType", "CPU_HIGH");
    }
}
