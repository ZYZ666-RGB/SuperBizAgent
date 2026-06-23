package org.example.context;

import java.util.ArrayList;
import java.util.List;

public class AiOpsContext {

    private String userId;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String taskDescription;
    private String currentStep;
    private String namespace = "default";
    private List<AiOpsEvidence> evidence = new ArrayList<>();
    private List<AiOpsStep> steps = new ArrayList<>();

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

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public List<AiOpsEvidence> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<AiOpsEvidence> evidence) {
        this.evidence = evidence == null ? new ArrayList<>() : evidence;
    }

    public List<AiOpsStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AiOpsStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }
}
