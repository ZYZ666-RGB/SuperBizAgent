package org.example.context;

import java.util.ArrayList;
import java.util.List;

public class ContextBuildRequest {

    public enum Scenario {
        CHAT,
        RAG_QA,
        AIOPS_PLANNER,
        AIOPS_EXECUTOR,
        AIOPS_REPLANNER,
        AIOPS_REPORTER
    }

    private RuntimeContext runtimeContext;
    private Scenario scenario = Scenario.CHAT;
    private String roleAndPolicies;
    private String task;
    private String outputInstructions;
    private boolean includeConversation = true;
    private boolean includeMemory = true;
    private boolean includeRag = true;
    private boolean includeToolResults = true;
    private List<ContextPacket> externalPackets = new ArrayList<>();

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    public String getRoleAndPolicies() {
        return roleAndPolicies;
    }

    public void setRoleAndPolicies(String roleAndPolicies) {
        this.roleAndPolicies = roleAndPolicies;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getOutputInstructions() {
        return outputInstructions;
    }

    public void setOutputInstructions(String outputInstructions) {
        this.outputInstructions = outputInstructions;
    }

    public boolean isIncludeConversation() {
        return includeConversation;
    }

    public void setIncludeConversation(boolean includeConversation) {
        this.includeConversation = includeConversation;
    }

    public boolean isIncludeMemory() {
        return includeMemory;
    }

    public void setIncludeMemory(boolean includeMemory) {
        this.includeMemory = includeMemory;
    }

    public boolean isIncludeRag() {
        return includeRag;
    }

    public void setIncludeRag(boolean includeRag) {
        this.includeRag = includeRag;
    }

    public boolean isIncludeToolResults() {
        return includeToolResults;
    }

    public void setIncludeToolResults(boolean includeToolResults) {
        this.includeToolResults = includeToolResults;
    }

    public List<ContextPacket> getExternalPackets() {
        return externalPackets;
    }

    public void setExternalPackets(List<ContextPacket> externalPackets) {
        this.externalPackets = externalPackets == null ? new ArrayList<>() : externalPackets;
    }
}
