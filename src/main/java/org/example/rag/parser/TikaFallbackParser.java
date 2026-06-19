package org.example.rag.parser;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.example.rag.model.ParsedDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(100)
@Component
public class TikaFallbackParser implements DocumentParser {

    private final FileTypeDetector fileTypeDetector;
    private final Tika tika = new Tika();

    public TikaFallbackParser(FileTypeDetector fileTypeDetector) {
        this.fileTypeDetector = fileTypeDetector;
    }

    @Override
    public boolean supports(String fileName, String contentType) {
        return true;
    }

    @Override
    public ParsedDocument parse(Path filePath, String namespace) {
        String fileName = filePath.getFileName().toString();
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            String plainText;
            try (InputStream inputStream = java.nio.file.Files.newInputStream(filePath)) {
                plainText = tika.parseToString(inputStream, metadata, 10 * 1024 * 1024);
            }
            if (plainText == null || plainText.isBlank()) {
                throw new IllegalStateException("Tika extracted empty text");
            }
            ParsedDocument document = new ParsedDocument();
            document.setFileName(fileName);
            document.setFileType(fileTypeDetector.extension(fileName));
            document.setContentType(defaultText(metadata.get(Metadata.CONTENT_TYPE), fileTypeDetector.contentType(filePath)));
            document.setNamespace(namespace);
            document.setSourcePath(filePath);
            document.setPlainText(plainText);
            document.setMarkdownContent("# " + fileName + "\n\n" + plainText.strip());
            document.setParserName("tika-fallback");
            document.setMetadata(new LinkedHashMap<>(Map.of(
                    "parser", "TikaFallbackParser",
                    "fallback", true)));
            return document;
        } catch (Exception e) {
            throw new IllegalStateException("Tika fallback parser failed: " + filePath, e);
        }
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue == null ? "" : defaultValue;
        }
        return value;
    }
}
