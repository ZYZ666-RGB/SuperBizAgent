package org.example.rag.parser;

import org.example.rag.model.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownFileParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesMarkdownFileDirectly() throws Exception {
        Path file = tempDir.resolve("runbook.md");
        Files.writeString(file, "# Runbook\n\nUse Milvus for vector search.");
        MarkdownFileParser parser = new MarkdownFileParser(new FileTypeDetector());

        ParsedDocument document = parser.parse(file, "default");

        assertThat(document.getMarkdownContent()).contains("# Runbook");
        assertThat(document.getParserName()).isEqualTo("markdown-file");
        assertThat(document.getMetadata()).containsEntry("wrapped", false);
    }

    @Test
    void wrapsCodeFileAsMarkdownCodeBlock() throws Exception {
        Path file = tempDir.resolve("UserService.java");
        Files.writeString(file, "class UserService {}\n");
        MarkdownFileParser parser = new MarkdownFileParser(new FileTypeDetector());

        ParsedDocument document = parser.parse(file, "default");

        assertThat(document.getMarkdownContent()).contains("# UserService.java");
        assertThat(document.getMarkdownContent()).contains("```java");
        assertThat(document.getMarkdownContent()).contains("class UserService");
    }
}
