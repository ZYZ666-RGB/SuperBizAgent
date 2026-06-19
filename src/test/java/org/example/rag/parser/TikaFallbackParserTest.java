package org.example.rag.parser;

import org.example.rag.model.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TikaFallbackParserTest {

    @TempDir
    Path tempDir;

    @Test
    void wrapsExtractedPlainTextAsMarkdown() throws Exception {
        Path file = tempDir.resolve("unknown.bin");
        Files.writeString(file, "Plain text extracted by fallback.");
        TikaFallbackParser parser = new TikaFallbackParser(new FileTypeDetector());

        ParsedDocument document = parser.parse(file, "ops");

        assertThat(document.getMarkdownContent()).contains("# unknown.bin");
        assertThat(document.getMarkdownContent()).contains("Plain text extracted");
        assertThat(document.getParserName()).isEqualTo("tika-fallback");
    }
}
