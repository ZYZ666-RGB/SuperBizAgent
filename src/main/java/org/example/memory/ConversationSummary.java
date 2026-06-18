package org.example.memory;

import java.time.LocalDateTime;

public class ConversationSummary {

    private Long id;
    private String userId;
    private String sessionId;
    private String summary;
    private Long summarizedUntilMessageId;
    private Integer summarizedMessageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Long getSummarizedUntilMessageId() {
        return summarizedUntilMessageId;
    }

    public void setSummarizedUntilMessageId(Long summarizedUntilMessageId) {
        this.summarizedUntilMessageId = summarizedUntilMessageId;
    }

    public Integer getSummarizedMessageCount() {
        return summarizedMessageCount;
    }

    public void setSummarizedMessageCount(Integer summarizedMessageCount) {
        this.summarizedMessageCount = summarizedMessageCount;
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
