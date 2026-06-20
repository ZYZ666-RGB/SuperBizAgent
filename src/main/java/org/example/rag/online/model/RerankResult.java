package org.example.rag.online.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RerankResult {
    private List<RetrievalCandidate> candidates = new ArrayList<>();
    private boolean success;
    private String provider;
    private String message;
    private Long timeMs;
}
