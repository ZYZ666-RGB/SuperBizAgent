package org.example.context;

import java.util.ArrayList;
import java.util.List;

public class ContextBuildResult {

    private RuntimeContext runtimeContext;
    private ConversationState conversationState;
    private String finalContext;
    private List<ContextPacket> selectedPackets = new ArrayList<>();
    private List<ContextPacket> droppedPackets = new ArrayList<>();
    private int estimatedTokens;

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public ConversationState getConversationState() {
        return conversationState;
    }

    public void setConversationState(ConversationState conversationState) {
        this.conversationState = conversationState;
    }

    public String getFinalContext() {
        return finalContext;
    }

    public void setFinalContext(String finalContext) {
        this.finalContext = finalContext;
    }

    public List<ContextPacket> getSelectedPackets() {
        return selectedPackets;
    }

    public void setSelectedPackets(List<ContextPacket> selectedPackets) {
        this.selectedPackets = selectedPackets == null ? new ArrayList<>() : selectedPackets;
    }

    public List<ContextPacket> getDroppedPackets() {
        return droppedPackets;
    }

    public void setDroppedPackets(List<ContextPacket> droppedPackets) {
        this.droppedPackets = droppedPackets == null ? new ArrayList<>() : droppedPackets;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }

    public void setEstimatedTokens(int estimatedTokens) {
        this.estimatedTokens = estimatedTokens;
    }
}
