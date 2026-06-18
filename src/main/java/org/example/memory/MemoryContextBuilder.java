package org.example.memory;

import org.example.memory.dto.ChatMessageDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryContextBuilder {

    private final MemoryProperties memoryProperties;
    private final ConversationMemoryService conversationMemoryService;
    private final SummaryMemoryService summaryMemoryService;
    private final LongTermMemoryService longTermMemoryService;

    public MemoryContextBuilder(
            MemoryProperties memoryProperties,
            ConversationMemoryService conversationMemoryService,
            SummaryMemoryService summaryMemoryService,
            LongTermMemoryService longTermMemoryService) {
        this.memoryProperties = memoryProperties;
        this.conversationMemoryService = conversationMemoryService;
        this.summaryMemoryService = summaryMemoryService;
        this.longTermMemoryService = longTermMemoryService;
    }

    public MemoryPromptContext buildForChat(String userId, String sessionId, String query) {
        MemoryPromptContext context = new MemoryPromptContext();
        if (!memoryProperties.isEnabled()) {
            return context;
        }

        summaryMemoryService.getSummary(userId, sessionId).ifPresent(context::setSessionSummary);
        List<ChatMessageDTO> recentMessages = conversationMemoryService.getRecentMessages(
                userId, sessionId, memoryProperties.getWindowSize());
        context.setRecentMessages(recentMessages);
        context.setSemanticMemories(longTermMemoryService.getSemanticMemoriesForPrompt(userId, query));
        context.setEpisodicMemories(longTermMemoryService.getEpisodicMemoriesForPrompt(userId));
        return context;
    }

    public MemoryPromptContext buildForChat(String userId, String sessionId) {
        return buildForChat(userId, sessionId, "");
    }
}
