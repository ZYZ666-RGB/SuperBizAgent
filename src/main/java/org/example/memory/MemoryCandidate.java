package org.example.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryCandidate {

    private String memoryType;
    private String scopeType;
    private String content;
    private String evidence;
    private List<String> entities = new ArrayList<>();
    private Boolean explicitSave;
    private String source;
    private Double evidenceScore;
    private Double stabilityScore;
    private Double futureUsefulnessScore;
    private Integer safetyScore;
    private Double importance;
    private Double confidence;
    private Boolean shouldSave;
    private String reason;

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities == null ? new ArrayList<>() : entities;
    }

    public Boolean getExplicitSave() {
        return explicitSave;
    }

    public void setExplicitSave(Boolean explicitSave) {
        this.explicitSave = explicitSave;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getEvidenceScore() {
        return evidenceScore;
    }

    public void setEvidenceScore(Double evidenceScore) {
        this.evidenceScore = evidenceScore;
    }

    public Double getStabilityScore() {
        return stabilityScore;
    }

    public void setStabilityScore(Double stabilityScore) {
        this.stabilityScore = stabilityScore;
    }

    public Double getFutureUsefulnessScore() {
        return futureUsefulnessScore;
    }

    public void setFutureUsefulnessScore(Double futureUsefulnessScore) {
        this.futureUsefulnessScore = futureUsefulnessScore;
    }

    public Integer getSafetyScore() {
        return safetyScore;
    }

    public void setSafetyScore(Integer safetyScore) {
        this.safetyScore = safetyScore;
    }

    public Double getImportance() {
        return importance;
    }

    public void setImportance(Double importance) {
        this.importance = importance;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Boolean getShouldSave() {
        return shouldSave;
    }

    public void setShouldSave(Boolean shouldSave) {
        this.shouldSave = shouldSave;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
