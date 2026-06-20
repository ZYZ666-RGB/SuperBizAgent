package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RagAnswer {
    private String answer;
    private List<Citation> citations = new ArrayList<>();
    private List<RetrievalCandidate> usedChunks = new ArrayList<>();
    private Double confidence;
    private Boolean supported;
    private String message;
    private RagTrace trace;
}
