package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RagTrace {
    private String query;
    private QueryAnalysis analysis;
    private List<String> rewrittenQueries = new ArrayList<>();
    private String hydeText;
    private Integer denseHitCount = 0;
    private Integer sparseHitCount = 0;
    private Integer fusedCount = 0;
    private Integer rerankCount = 0;
    private Long queryRewriteTimeMs = 0L;
    private Long denseRetrieveTimeMs = 0L;
    private Long sparseRetrieveTimeMs = 0L;
    private Long rerankTimeMs = 0L;
    private Long generationTimeMs = 0L;
    private Long totalTimeMs = 0L;
}
