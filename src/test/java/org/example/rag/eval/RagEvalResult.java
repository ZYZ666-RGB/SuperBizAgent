package org.example.rag.eval;

import java.util.ArrayList;
import java.util.List;

public class RagEvalResult {

    private String caseId;
    private boolean shouldAnswer;
    private boolean supported;
    private double confidence;
    private boolean retrievalPassed;
    private boolean citationPassed;
    private boolean requiredTermsPassed;
    private boolean confidencePassed;
    private boolean refusalPassed;
    private List<String> usedChunkIds = new ArrayList<>();
    private String message;

    public boolean passed() {
        if (shouldAnswer) {
            return supported
                    && retrievalPassed
                    && citationPassed
                    && requiredTermsPassed
                    && confidencePassed;
        }
        return refusalPassed;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public boolean isShouldAnswer() {
        return shouldAnswer;
    }

    public void setShouldAnswer(boolean shouldAnswer) {
        this.shouldAnswer = shouldAnswer;
    }

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isRetrievalPassed() {
        return retrievalPassed;
    }

    public void setRetrievalPassed(boolean retrievalPassed) {
        this.retrievalPassed = retrievalPassed;
    }

    public boolean isCitationPassed() {
        return citationPassed;
    }

    public void setCitationPassed(boolean citationPassed) {
        this.citationPassed = citationPassed;
    }

    public boolean isRequiredTermsPassed() {
        return requiredTermsPassed;
    }

    public void setRequiredTermsPassed(boolean requiredTermsPassed) {
        this.requiredTermsPassed = requiredTermsPassed;
    }

    public boolean isConfidencePassed() {
        return confidencePassed;
    }

    public void setConfidencePassed(boolean confidencePassed) {
        this.confidencePassed = confidencePassed;
    }

    public boolean isRefusalPassed() {
        return refusalPassed;
    }

    public void setRefusalPassed(boolean refusalPassed) {
        this.refusalPassed = refusalPassed;
    }

    public List<String> getUsedChunkIds() {
        return usedChunkIds;
    }

    public void setUsedChunkIds(List<String> usedChunkIds) {
        this.usedChunkIds = usedChunkIds == null ? new ArrayList<>() : usedChunkIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "RagEvalResult{"
                + "caseId='" + caseId + '\''
                + ", shouldAnswer=" + shouldAnswer
                + ", supported=" + supported
                + ", confidence=" + confidence
                + ", retrievalPassed=" + retrievalPassed
                + ", citationPassed=" + citationPassed
                + ", requiredTermsPassed=" + requiredTermsPassed
                + ", confidencePassed=" + confidencePassed
                + ", refusalPassed=" + refusalPassed
                + ", usedChunkIds=" + usedChunkIds
                + ", message='" + message + '\''
                + '}';
    }
}
