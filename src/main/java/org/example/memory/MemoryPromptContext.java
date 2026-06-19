package org.example.memory;

import org.example.memory.dto.ChatMessageDTO;

import java.util.ArrayList;
import java.util.List;

public class MemoryPromptContext {

    private String sessionSummary;
    private List<ChatMessageDTO> recentMessages = new ArrayList<>();
    private List<UserMemory> semanticMemories = new ArrayList<>();
    private List<UserMemory> episodicMemories = new ArrayList<>();
    private List<String> graphRelations = new ArrayList<>();

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

    public List<UserMemory> getSemanticMemories() {
        return semanticMemories;
    }

    public void setSemanticMemories(List<UserMemory> semanticMemories) {
        this.semanticMemories = semanticMemories == null ? new ArrayList<>() : semanticMemories;
    }

    public List<UserMemory> getEpisodicMemories() {
        return episodicMemories;
    }

    public void setEpisodicMemories(List<UserMemory> episodicMemories) {
        this.episodicMemories = episodicMemories == null ? new ArrayList<>() : episodicMemories;
    }

    public List<String> getGraphRelations() {
        return graphRelations;
    }

    public void setGraphRelations(List<String> graphRelations) {
        this.graphRelations = graphRelations == null ? new ArrayList<>() : graphRelations;
    }
}
