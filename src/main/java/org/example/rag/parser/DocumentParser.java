package org.example.rag.parser;

import org.example.rag.model.ParsedDocument;

import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String fileName, String contentType);

    ParsedDocument parse(Path filePath, String namespace);
}
