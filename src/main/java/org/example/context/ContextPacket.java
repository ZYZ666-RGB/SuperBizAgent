package org.example.context;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContextPacket {

    private ContextSourceType type;
    private String sourceId;
    private String title;
    private String content;
    private String summary;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private double relevanceScore;
    private double recencyScore;
    private double importanceScore;
    private double finalScore;
    private int tokenEstimate;
    private LocalDateTime createdAt;

    public ContextSourceType getType() {
        return type;
    }

    public void setType(ContextSourceType type) {
        this.type = type;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public double getRecencyScore() {
        return recencyScore;
    }

    public void setRecencyScore(double recencyScore) {
        this.recencyScore = recencyScore;
    }

    public double getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(double importanceScore) {
        this.importanceScore = importanceScore;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public void setTokenEstimate(int tokenEstimate) {
        this.tokenEstimate = tokenEstimate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
