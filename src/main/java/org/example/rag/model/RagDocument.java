package org.example.rag.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RagDocument {

    private Long id;
    private String documentId;
    private String namespace;
    private String fileName;
    private String fileHash;
    private String fileType;
    private String sourcePath;
    private String markdownPath;
    private String parserName;
    private IndexStatus status;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
