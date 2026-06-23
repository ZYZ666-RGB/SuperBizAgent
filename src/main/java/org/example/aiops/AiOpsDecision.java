package org.example.aiops;

import java.util.LinkedHashMap;
import java.util.Map;

public class AiOpsDecision {

    private AiOpsDecisionType decision = AiOpsDecisionType.EXECUTE;
    private String step;
    private String rationale;
    private String expectedEvidence;
    private String toolName;
    private Map<String, Object> toolParameters = new LinkedHashMap<>();
    private String failureReason;
    private String rawText;

    public AiOpsDecisionType getDecision() {
        return decision;
    }

    public void setDecision(AiOpsDecisionType decision) {
        this.decision = decision == null ? AiOpsDecisionType.EXECUTE : decision;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getExpectedEvidence() {
        return expectedEvidence;
    }

    public void setExpectedEvidence(String expectedEvidence) {
        this.expectedEvidence = expectedEvidence;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getToolParameters() {
        return toolParameters;
    }

    public void setToolParameters(Map<String, Object> toolParameters) {
        this.toolParameters = toolParameters == null ? new LinkedHashMap<>() : toolParameters;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
