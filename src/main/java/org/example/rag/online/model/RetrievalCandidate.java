package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class RetrievalCandidate {
    private String chunkId;
    private String documentId;
    private String parentChunkId;
    private String namespace;
    private String fileName;
    private String fileType;
    private String sourcePath;
    private String headingPath;
    private Integer chunkIndex;
    private String content;
    private String embeddingContent;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Double denseScore;
    private Double sparseScore;
    private Double fusedScore;
    private Double rerankScore;
    private List<String> matchedBy = new ArrayList<>();
}
