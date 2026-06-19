package org.example.rag.chunk;

import org.example.rag.config.RagProperties;
import org.example.rag.markdown.MarkdownSectionParser;
import org.example.rag.model.ParsedDocument;
import org.example.rag.model.RagChunk;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkServiceTest {

    @Test
    void createsHeadingAwareChildChunksAndParentLinks() {
        RagProperties properties = new RagProperties();
        properties.getChunk().setTargetTokens(20);
        properties.getChunk().setMinTokens(1);
        TokenEstimator tokenEstimator = new TokenEstimator();
        MarkdownChunkService chunkService = new MarkdownChunkService(
                new MarkdownSectionParser(tokenEstimator),
                tokenEstimator,
                properties);
        ParentChildChunkService parentService = new ParentChildChunkService(properties, tokenEstimator);
        ParsedDocument document = document();

        List<RagChunk> children = chunkService.chunk(document, """
                # Runbook
                ## Restart
                Restart order-service when ERR_TIMEOUT appears.

                ## Rollback
                Roll back after DB_TIMEOUT.
                """);
        List<RagChunk> allChunks = parentService.attachParents(document, children);

        assertThat(children).allSatisfy(chunk -> {
            assertThat(chunk.getChunkId()).isNotBlank();
            assertThat(chunk.getHeadingPath()).startsWith("Runbook");
        });
        assertThat(allChunks).anySatisfy(chunk -> assertThat(chunk.getParent()).isTrue());
        assertThat(allChunks.stream().filter(chunk -> !Boolean.TRUE.equals(chunk.getParent())))
                .allSatisfy(chunk -> assertThat(chunk.getParentChunkId()).isNotBlank());
    }

    private ParsedDocument document() {
        ParsedDocument document = new ParsedDocument();
        document.setDocumentId("doc-1");
        document.setNamespace("ops");
        document.setFileName("runbook.md");
        document.setFileType("md");
        document.setSourcePath(Path.of("runbook.md"));
        return document;
    }
}
