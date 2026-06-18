package org.example.memory;

import org.example.memory.dto.ChatMessageDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationMemoryService {

    void saveMessage(String userId, String sessionId, String role, String content);

    List<ChatMessageDTO> getRecentMessages(String userId, String sessionId, int roundLimit);

    long countMessages(String userId, String sessionId);

    void clearSession(String userId, String sessionId);

    Optional<LocalDateTime> getSessionCreatedAt(String userId, String sessionId);
}
