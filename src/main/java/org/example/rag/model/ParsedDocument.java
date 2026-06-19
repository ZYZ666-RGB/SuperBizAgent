package org.example.rag.model;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ParsedDocument {

    private String documentId;
    private String fileName;
    private String fileHash;
    private String fileType;
    private String contentType;
    private String namespace;
    private Path sourcePath;
    private String markdownContent;
    private String plainText;
    private String parserName;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
