package org.example.rag.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexResult {

    private boolean success;
    private String documentId;
    private String fileName;
    private IndexStatus status;
    private int chunkCount;
    private String sourcePath;
    private String markdownPath;
    private String chunkPath;
    private String message;
    private String errorMessage;
}
