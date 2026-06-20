package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RagQueryResult {
    private List<RetrievalCandidate> candidates = new ArrayList<>();
    private RagTrace trace;
}
