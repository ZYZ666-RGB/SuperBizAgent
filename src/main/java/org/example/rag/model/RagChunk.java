package org.example.rag.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class RagChunk {

    private String chunkId;
    private String documentId;
    private String parentChunkId;
    private Boolean parent = false;
    private String parentHeadingPath;
    private String namespace;
    private String fileName;
    private String fileType;
    private String sourcePath;
    private String headingPath;
    private Integer chunkIndex;
    private Integer startOffset;
    private Integer endOffset;
    private Integer tokenCount;
    private String content;
    private String embeddingContent;
    private String summary;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Boolean embeddingFailed = false;
    private String embeddingError;
}
