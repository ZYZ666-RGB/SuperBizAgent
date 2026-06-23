package org.example.aiops;

import org.example.context.AiOpsEvidence;
import org.example.context.AiOpsStep;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AiOpsWorkflowState {

    private String userId;
    private String sessionId;
    private String taskId;
    private AiOpsWorkflowStage stage = AiOpsWorkflowStage.PLANNING;
    private List<AiOpsStep> steps = new ArrayList<>();
    private List<AiOpsEvidence> evidence = new ArrayList<>();
    private String lastPlannerOutput;
    private String lastExecutorOutput;
    private String finalReport;
    private String failureReason;
    private int iteration;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

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

    public AiOpsWorkflowStage getStage() {
        return stage;
    }

    public void setStage(AiOpsWorkflowStage stage) {
        this.stage = stage == null ? AiOpsWorkflowStage.PLANNING : stage;
        this.updatedAt = LocalDateTime.now();
    }

    public List<AiOpsStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AiOpsStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    public List<AiOpsEvidence> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<AiOpsEvidence> evidence) {
        this.evidence = evidence == null ? new ArrayList<>() : evidence;
    }

    public String getLastPlannerOutput() {
        return lastPlannerOutput;
    }

    public void setLastPlannerOutput(String lastPlannerOutput) {
        this.lastPlannerOutput = lastPlannerOutput;
        this.updatedAt = LocalDateTime.now();
    }

    public String getLastExecutorOutput() {
        return lastExecutorOutput;
    }

    public void setLastExecutorOutput(String lastExecutorOutput) {
        this.lastExecutorOutput = lastExecutorOutput;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFinalReport() {
        return finalReport;
    }

    public void setFinalReport(String finalReport) {
        this.finalReport = finalReport;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
        this.updatedAt = LocalDateTime.now();
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
