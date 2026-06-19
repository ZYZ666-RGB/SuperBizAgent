package org.example.memory.task;

import java.time.LocalDateTime;

public class AgentTaskState {

    private Long id;
    private String userId;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String stage;
    private String plannerPlan;
    private String executorFeedback;
    private String toolResults;
    private String finalReport;
    private String status;
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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getPlannerPlan() {
        return plannerPlan;
    }

    public void setPlannerPlan(String plannerPlan) {
        this.plannerPlan = plannerPlan;
    }

    public String getExecutorFeedback() {
        return executorFeedback;
    }

    public void setExecutorFeedback(String executorFeedback) {
        this.executorFeedback = executorFeedback;
    }

    public String getToolResults() {
        return toolResults;
    }

    public void setToolResults(String toolResults) {
        this.toolResults = toolResults;
    }

    public String getFinalReport() {
        return finalReport;
    }

    public void setFinalReport(String finalReport) {
        this.finalReport = finalReport;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
