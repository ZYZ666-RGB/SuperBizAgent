package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EvidenceContext {
    private String contextText;
    private List<Citation> citations = new ArrayList<>();
    private List<RetrievalCandidate> usedChunks = new ArrayList<>();
    private Integer totalTokens;
}
