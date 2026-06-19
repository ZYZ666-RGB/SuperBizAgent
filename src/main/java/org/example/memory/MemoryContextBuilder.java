package org.example.memory;

import org.example.memory.dto.ChatMessageDTO;
import org.example.memory.graph.GraphMemoryRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryContextBuilder {

    private final MemoryProperties memoryProperties;
    private final ConversationMemoryService conversationMemoryService;
    private final SummaryMemoryService summaryMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final GraphMemoryRetriever graphMemoryRetriever;

    public MemoryContextBuilder(
            MemoryProperties memoryProperties,
            ConversationMemoryService conversationMemoryService,
            SummaryMemoryService summaryMemoryService,
            LongTermMemoryService longTermMemoryService) {
        this(memoryProperties, conversationMemoryService, summaryMemoryService, longTermMemoryService,
                (GraphMemoryRetriever) null);
    }

    @Autowired
    public MemoryContextBuilder(
            MemoryProperties memoryProperties,
            ConversationMemoryService conversationMemoryService,
            SummaryMemoryService summaryMemoryService,
            LongTermMemoryService longTermMemoryService,
            ObjectProvider<GraphMemoryRetriever> graphMemoryRetrieverProvider) {
        this(memoryProperties, conversationMemoryService, summaryMemoryService, longTermMemoryService,
                graphMemoryRetrieverProvider.getIfAvailable());
    }

    private MemoryContextBuilder(
            MemoryProperties memoryProperties,
            ConversationMemoryService conversationMemoryService,
            SummaryMemoryService summaryMemoryService,
            LongTermMemoryService longTermMemoryService,
            GraphMemoryRetriever graphMemoryRetriever) {
        this.memoryProperties = memoryProperties;
        this.conversationMemoryService = conversationMemoryService;
        this.summaryMemoryService = summaryMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.graphMemoryRetriever = graphMemoryRetriever;
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
        if (graphMemoryRetriever != null) {
            context.setGraphRelations(graphMemoryRetriever.searchRelations(userId, query));
        }
        return context;
    }

    public MemoryPromptContext buildForChat(String userId, String sessionId) {
        return buildForChat(userId, sessionId, "");
    }
}
