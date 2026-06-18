package org.example.memory;

import java.time.LocalDateTime;

public class UserMemory {

    private Long id;
    private String memoryId;
    private String userId;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String appId;
    private String memoryType;
    private String scopeType;
    private String content;
    private String evidence;
    private String entities;
    private String metadata;
    private String source;
    private Double importance;
    private Double confidence;
    private Double evidenceScore;
    private Double stabilityScore;
    private Double futureUsefulnessScore;
    private Integer safetyScore;
    private Integer accessCount;
    private LocalDateTime lastAccessedAt;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

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

    public String getEntities() {
        return entities;
    }

    public void setEntities(String entities) {
        this.entities = entities;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
