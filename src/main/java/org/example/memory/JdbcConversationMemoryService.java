package org.example.memory;

import org.example.memory.dto.ChatMessageDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class JdbcConversationMemoryService implements ConversationMemoryService {

    private final ConversationMessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;

    public JdbcConversationMemoryService(
            ConversationMessageRepository messageRepository,
            ConversationSummaryRepository summaryRepository) {
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
    }

    @Override
    public void saveMessage(String userId, String sessionId, String role, String content) {
        messageRepository.insert(userId, sessionId, role, content, estimateTokenCount(content));
    }

    @Override
    public List<ChatMessageDTO> getRecentMessages(String userId, String sessionId, int roundLimit) {
        int messageLimit = Math.max(1, roundLimit) * 2;
        return messageRepository.findRecent(userId, sessionId, messageLimit)
                .stream()
                .map(message -> new ChatMessageDTO(message.getId(), message.getRole(), message.getContent()))
                .toList();
    }

    @Override
    public long countMessages(String userId, String sessionId) {
        return messageRepository.countBySession(userId, sessionId);
    }

    @Override
    @Transactional
    public void clearSession(String userId, String sessionId) {
        messageRepository.deleteBySession(userId, sessionId);
        summaryRepository.deleteBySession(userId, sessionId);
    }

    @Override
    public Optional<LocalDateTime> getSessionCreatedAt(String userId, String sessionId) {
        return messageRepository.findSessionCreatedAt(userId, sessionId);
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }
}
