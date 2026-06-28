package org.example.rag.eval;

import java.util.ArrayList;
import java.util.List;

public class RagEvalCase {

    private String id;
    private String query;
    private String namespace = "default";
    private Integer topK = 3;
    private boolean shouldAnswer = true;
    private List<String> expectedChunkIds = new ArrayList<>();
    private List<String> requiredAnswerTerms = new ArrayList<>();
    private double minConfidence = 0.55;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public boolean isShouldAnswer() {
        return shouldAnswer;
    }

    public void setShouldAnswer(boolean shouldAnswer) {
        this.shouldAnswer = shouldAnswer;
    }

    public List<String> getExpectedChunkIds() {
        return expectedChunkIds;
    }

    public void setExpectedChunkIds(List<String> expectedChunkIds) {
        this.expectedChunkIds = expectedChunkIds == null ? new ArrayList<>() : expectedChunkIds;
    }

    public List<String> getRequiredAnswerTerms() {
        return requiredAnswerTerms;
    }

    public void setRequiredAnswerTerms(List<String> requiredAnswerTerms) {
        this.requiredAnswerTerms = requiredAnswerTerms == null ? new ArrayList<>() : requiredAnswerTerms;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }
}
