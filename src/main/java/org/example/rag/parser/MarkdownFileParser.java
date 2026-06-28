package org.example.rag.parser;

import org.example.rag.model.ParsedDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(10)
@Component
public class MarkdownFileParser implements DocumentParser {

    private final FileTypeDetector fileTypeDetector;

    public MarkdownFileParser(FileTypeDetector fileTypeDetector) {
        this.fileTypeDetector = fileTypeDetector;
    }

    @Override
    public boolean supports(String fileName, String contentType) {
        return fileTypeDetector.isMarkdownLike(fileName);
    }

    @Override
    public ParsedDocument parse(Path filePath, String namespace) {
        try {
            String fileName = filePath.getFileName().toString();
            String extension = fileTypeDetector.extension(fileName);
            String raw = Files.readString(filePath, StandardCharsets.UTF_8);
            String markdown = toMarkdown(fileName, extension, raw);

            ParsedDocument document = new ParsedDocument();
            document.setFileName(fileName);
            document.setFileType(extension);
            document.setContentType(fileTypeDetector.contentType(filePath));
            document.setNamespace(namespace);
            document.setSourcePath(filePath);
            document.setMarkdownContent(markdown);
            document.setPlainText(raw);
            document.setParserName("markdown-file");
            document.setMetadata(new LinkedHashMap<>(Map.of(
                    "parser", "MarkdownFileParser",
                    "wrapped", !"md".equals(extension))));
            return document;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse markdown-like file: " + filePath, e);
        }
    }

    private String toMarkdown(String fileName, String extension, String raw) {
        if ("md".equals(extension) || "markdown".equals(extension)) {
            return raw;
        }
        if ("txt".equals(extension)) {
            return "# " + fileName + "\n\n" + raw;
        }
        String language = "log".equals(extension) ? "log" : fileTypeDetector.codeFenceLanguage(fileName);
        return "# " + fileName + "\n\n```" + language + "\n" + raw.stripTrailing() + "\n```\n";
    }
}
