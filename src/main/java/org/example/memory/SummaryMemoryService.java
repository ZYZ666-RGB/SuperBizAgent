package org.example.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SummaryMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(SummaryMemoryService.class);

    private final MemoryProperties memoryProperties;
    private final ConversationMessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final SummaryPromptBuilder summaryPromptBuilder;

    public SummaryMemoryService(
            MemoryProperties memoryProperties,
            ConversationMessageRepository messageRepository,
            ConversationSummaryRepository summaryRepository,
            SummaryPromptBuilder summaryPromptBuilder) {
        this.memoryProperties = memoryProperties;
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
        this.summaryPromptBuilder = summaryPromptBuilder;
    }

    public Optional<String> getSummary(String userId, String sessionId) {
        return summaryRepository.findBySession(userId, sessionId)
                .map(ConversationSummary::getSummary)
                .filter(summary -> !summary.isBlank());
    }

    public void refreshSummaryIfNeeded(String userId, String sessionId, DashScopeChatModel chatModel) {
        if (!memoryProperties.isEnabled()) {
            return;
        }

        try {
            long messageCount = messageRepository.countBySession(userId, sessionId);
            if (messageCount <= memoryProperties.getSummaryThreshold()) {
                return;
            }

            int recentMessageLimit = memoryProperties.getRecentMessageLimit();
            Optional<Long> cutoffMessageId = messageRepository.findSummaryCutoffMessageId(
                    userId, sessionId, recentMessageLimit);
            if (cutoffMessageId.isEmpty()) {
                return;
            }

            ConversationSummary existingSummary = summaryRepository.findBySession(userId, sessionId).orElse(null);
            long summarizedUntilMessageId = existingSummary == null
                    ? 0L
                    : existingSummary.getSummarizedUntilMessageId();
            if (cutoffMessageId.get() <= summarizedUntilMessageId) {
                return;
            }

            List<ConversationMessage> messagesToSummarize = messageRepository.findMessagesForSummary(
                    userId, sessionId, summarizedUntilMessageId, cutoffMessageId.get());
            if (messagesToSummarize.isEmpty()) {
                return;
            }

            String oldSummary = existingSummary == null ? "" : existingSummary.getSummary();
            String prompt = summaryPromptBuilder.build(oldSummary, messagesToSummarize);
            String newSummary = summarize(chatModel, prompt);
            if (newSummary == null || newSummary.isBlank()) {
                logger.warn("Summary generation returned empty text. userId={}, sessionId={}", userId, sessionId);
                return;
            }

            long lastSummarizedId = messagesToSummarize.get(messagesToSummarize.size() - 1).getId();
            int summarizedCount = (existingSummary == null ? 0 : existingSummary.getSummarizedMessageCount())
                    + messagesToSummarize.size();
            summaryRepository.upsert(userId, sessionId, newSummary.trim(), lastSummarizedId, summarizedCount);
            logger.info("Updated conversation summary. userId={}, sessionId={}, summarizedUntilMessageId={}",
                    userId, sessionId, lastSummarizedId);
        } catch (Exception e) {
            logger.warn("Failed to refresh conversation summary. userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
        }
    }

    private String summarize(DashScopeChatModel chatModel, String prompt) {
        ChatResponse response = chatModel.call(new Prompt(prompt));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
