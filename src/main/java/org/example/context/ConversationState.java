package org.example.context;

import org.example.memory.dto.ChatMessageDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConversationState {

    private String sessionSummary;
    private List<ChatMessageDTO> recentMessages = new ArrayList<>();
    private long messageCount;
    private Map<String, Object> agentState = new LinkedHashMap<>();

    public String getSessionSummary() {
        return sessionSummary;
    }

    public void setSessionSummary(String sessionSummary) {
        this.sessionSummary = sessionSummary;
    }

    public List<ChatMessageDTO> getRecentMessages() {
        return recentMessages;
    }

    public void setRecentMessages(List<ChatMessageDTO> recentMessages) {
        this.recentMessages = recentMessages == null ? new ArrayList<>() : recentMessages;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(long messageCount) {
        this.messageCount = messageCount;
    }

    public Map<String, Object> getAgentState() {
        return agentState;
    }

    public void setAgentState(Map<String, Object> agentState) {
        this.agentState = agentState == null ? new LinkedHashMap<>() : agentState;
    }
}
