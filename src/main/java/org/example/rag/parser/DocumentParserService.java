package org.example.rag.parser;

import org.example.rag.config.RagProperties;
import org.example.rag.model.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class DocumentParserService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParserService.class);

    private final List<DocumentParser> parsers;
    private final RagProperties ragProperties;
    private final FileTypeDetector fileTypeDetector;

    public DocumentParserService(
            List<DocumentParser> parsers,
            RagProperties ragProperties,
            FileTypeDetector fileTypeDetector) {
        this.parsers = parsers;
        this.ragProperties = ragProperties;
        this.fileTypeDetector = fileTypeDetector;
    }

    public ParsedDocument parse(Path filePath, String namespace) {
        String fileName = filePath.getFileName().toString();
        String contentType = fileTypeDetector.contentType(filePath);
        Exception lastError = null;

        for (DocumentParser parser : parsers) {
            if (!parser.supports(fileName, contentType)) {
                continue;
            }
            if (parser instanceof TikaFallbackParser && !ragProperties.getParser().isFallbackToTika()) {
                continue;
            }
            try {
                ParsedDocument document = parser.parse(filePath, namespace);
                validate(document);
                logger.info("[RAG-OFFLINE] Parsed file with {}: {}", parser.getClass().getSimpleName(), fileName);
                return document;
            } catch (Exception e) {
                lastError = e;
                logger.warn("[RAG-OFFLINE] Parser {} failed for {}: {}",
                        parser.getClass().getSimpleName(), fileName, e.getMessage());
            }
        }
        throw new IllegalStateException("No parser produced markdown for " + fileName, lastError);
    }

    private void validate(ParsedDocument document) {
        if (document == null || document.getMarkdownContent() == null || document.getMarkdownContent().isBlank()) {
            throw new IllegalStateException("Parsed markdown content is empty");
        }
    }
}
