package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Citation {
    private Integer index;
    private String chunkId;
    private String documentId;
    private String fileName;
    private String headingPath;
    private Integer chunkIndex;
    private String sourcePath;
    private String snippet;
}
