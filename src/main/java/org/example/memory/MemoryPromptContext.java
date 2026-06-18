package org.example.memory;

import org.example.memory.dto.ChatMessageDTO;

import java.util.ArrayList;
import java.util.List;

public class MemoryPromptContext {

    private String sessionSummary;
    private List<ChatMessageDTO> recentMessages = new ArrayList<>();

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
}
