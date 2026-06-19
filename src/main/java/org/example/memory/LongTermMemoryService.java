package org.example.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.memory.graph.GraphMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LongTermMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryService.class);

    private final MemoryProperties memoryProperties;
    private final UserMemoryRepository userMemoryRepository;
    private final MemoryExtractorService memoryExtractorService;
    private final MemoryAdmissionService memoryAdmissionService;
    private final MemoryDedupService memoryDedupService;
    private final MemoryVectorService memoryVectorService;
    private final GraphMemoryService graphMemoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LongTermMemoryService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            MemoryExtractorService memoryExtractorService,
            MemoryAdmissionService memoryAdmissionService,
            MemoryDedupService memoryDedupService,
            MemoryVectorService memoryVectorService) {
        this(memoryProperties, userMemoryRepository, memoryExtractorService, memoryAdmissionService,
                memoryDedupService, memoryVectorService, (GraphMemoryService) null);
    }

    @Autowired
    public LongTermMemoryService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            MemoryExtractorService memoryExtractorService,
            MemoryAdmissionService memoryAdmissionService,
            MemoryDedupService memoryDedupService,
            MemoryVectorService memoryVectorService,
            ObjectProvider<GraphMemoryService> graphMemoryServiceProvider) {
        this(memoryProperties, userMemoryRepository, memoryExtractorService, memoryAdmissionService,
                memoryDedupService, memoryVectorService, graphMemoryServiceProvider.getIfAvailable());
    }

    private LongTermMemoryService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            MemoryExtractorService memoryExtractorService,
            MemoryAdmissionService memoryAdmissionService,
            MemoryDedupService memoryDedupService,
            MemoryVectorService memoryVectorService,
            GraphMemoryService graphMemoryService) {
        this.memoryProperties = memoryProperties;
        this.userMemoryRepository = userMemoryRepository;
        this.memoryExtractorService = memoryExtractorService;
        this.memoryAdmissionService = memoryAdmissionService;
        this.memoryDedupService = memoryDedupService;
        this.memoryVectorService = memoryVectorService;
        this.graphMemoryService = graphMemoryService;
    }

    public List<UserMemory> getSemanticMemoriesForPrompt(String userId, String query) {
        if (!isLongTermEnabled()) {
            return List.of();
        }
        int topK = memoryProperties.getLongTerm().getTopK();
        if (query != null && !query.isBlank()) {
            try {
                List<UserMemory> vectorResults = memoryVectorService.searchMemories(userId, query, null, topK);
                if (!vectorResults.isEmpty()) {
                    userMemoryRepository.markAccessed(vectorResults.stream()
                            .map(UserMemory::getMemoryId)
                            .toList());
                    return vectorResults;
                }
            } catch (Exception e) {
                logger.warn("Vector memory search failed, falling back to MySQL. userId={}, error={}",
                        userId, e.getMessage());
            }
        }
        List<UserMemory> mysqlResults = userMemoryRepository.findEnabledForPrompt(userId, topK);
        userMemoryRepository.markAccessed(mysqlResults.stream()
                .map(UserMemory::getMemoryId)
                .toList());
        return mysqlResults;
    }

    public List<UserMemory> getEpisodicMemoriesForPrompt(String userId) {
        if (!isLongTermEnabled()) {
            return List.of();
        }
        return userMemoryRepository.findRecentEpisodicForPrompt(userId, 3);
    }

    public List<UserMemory> searchMemories(String userId, String query, String memoryType, int limit) {
        if (!isLongTermEnabled()) {
            return List.of();
        }
        List<String> types = memoryType == null || memoryType.isBlank() ? null : List.of(memoryType);
        if (query != null && !query.isBlank()) {
            try {
                List<UserMemory> vectorResults = memoryVectorService.searchMemories(userId, query, types, limit);
                if (!vectorResults.isEmpty()) {
                    userMemoryRepository.markAccessed(vectorResults.stream()
                            .map(UserMemory::getMemoryId)
                            .toList());
                    return vectorResults;
                }
            } catch (Exception e) {
                logger.warn("Vector memory tool search failed, falling back to MySQL. userId={}, error={}",
                        userId, e.getMessage());
            }
        }
        List<UserMemory> mysqlResults = userMemoryRepository.searchEnabledByContent(userId, query, memoryType, limit);
        userMemoryRepository.markAccessed(mysqlResults.stream()
                .map(UserMemory::getMemoryId)
                .toList());
        return mysqlResults;
    }

    public List<UserMemory> extractAndSaveAfterChat(
            String userId,
            String sessionId,
            String question,
            String answer,
            DashScopeChatModel chatModel) {
        if (!isLongTermEnabled()) {
            return List.of();
        }

        try {
            List<MemoryCandidate> candidates = memoryExtractorService.extract(question, answer, chatModel);
            return candidates.stream()
                    .map(candidate -> saveCandidate(userId, sessionId, candidate))
                    .flatMap(List::stream)
                    .toList();
        } catch (Exception e) {
            logger.warn("Failed to run long-term memory pipeline. userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
            return List.of();
        }
    }

    public List<UserMemory> saveCandidate(String userId, String sessionId, MemoryCandidate candidate) {
        if (!memoryAdmissionService.shouldSave(candidate)) {
            return List.of();
        }
        if (memoryDedupService.isDuplicate(userId, candidate)) {
            return List.of();
        }

        UserMemory memory = toUserMemory(userId, sessionId, candidate);
        userMemoryRepository.insert(memory);
        try {
            memoryVectorService.indexMemory(memory);
        } catch (Exception e) {
            logger.warn("Failed to index long-term memory vector. memoryId={}, error={}",
                    memory.getMemoryId(), e.getMessage());
        }
        try {
            if (graphMemoryService != null) {
                graphMemoryService.indexMemory(memory);
            }
        } catch (Exception e) {
            logger.warn("Failed to index graph memory. memoryId={}, error={}",
                    memory.getMemoryId(), e.getMessage());
        }
        logger.info("Saved long-term memory. userId={}, sessionId={}, type={}, memoryId={}",
                userId, sessionId, memory.getMemoryType(), memory.getMemoryId());
        return List.of(memory);
    }

    public List<UserMemory> addManualMemory(
            String userId,
            String content,
            String memoryType,
            String scopeType,
            double importance) {
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.setMemoryType(defaultText(memoryType, "semantic"));
        candidate.setScopeType(defaultText(scopeType, "user"));
        candidate.setContent(content);
        candidate.setEvidence(content);
        candidate.setEntities(List.of());
        candidate.setExplicitSave(true);
        candidate.setSource("user_explicit");
        candidate.setEvidenceScore(1.0);
        candidate.setStabilityScore(0.9);
        candidate.setFutureUsefulnessScore(0.9);
        candidate.setSafetyScore(1);
        candidate.setImportance(Math.max(0.0, Math.min(1.0, importance)));
        candidate.setConfidence(0.95);
        candidate.setShouldSave(true);
        candidate.setReason("Added through MemoryTools.");
        return saveCandidate(userId, null, candidate);
    }

    public boolean updateMemory(String userId, String memoryId, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            return false;
        }
        MemoryCandidate updateCandidate = new MemoryCandidate();
        updateCandidate.setContent(newContent);
        updateCandidate.setEvidence(newContent);
        updateCandidate.setSource("user_explicit");
        updateCandidate.setExplicitSave(true);
        updateCandidate.setSafetyScore(1);
        updateCandidate.setShouldSave(true);
        if (!memoryAdmissionService.shouldSave(updateCandidate)) {
            return false;
        }
        if (userMemoryRepository.findEnabledByMemoryId(userId, memoryId).isEmpty()) {
            return false;
        }
        userMemoryRepository.updateContent(userId, memoryId, newContent.trim());
        userMemoryRepository.findEnabledByMemoryId(userId, memoryId).ifPresent(memory -> {
            try {
                memoryVectorService.deleteMemory(userId, memoryId);
                memoryVectorService.indexMemory(memory);
            } catch (Exception e) {
                logger.warn("Failed to refresh memory vector after update. userId={}, memoryId={}, error={}",
                        userId, memoryId, e.getMessage());
            }
            try {
                if (graphMemoryService != null) {
                    graphMemoryService.indexMemory(memory);
                }
            } catch (Exception e) {
                logger.warn("Failed to refresh graph memory after update. userId={}, memoryId={}, error={}",
                        userId, memoryId, e.getMessage());
            }
        });
        return true;
    }

    public boolean removeMemory(String userId, String memoryId) {
        if (userMemoryRepository.findEnabledByMemoryId(userId, memoryId).isEmpty()) {
            return false;
        }
        disableMemory(userId, memoryId);
        return true;
    }

    public void disableMemory(String userId, String memoryId) {
        userMemoryRepository.disableByMemoryId(userId, memoryId);
        try {
            memoryVectorService.deleteMemory(userId, memoryId);
        } catch (Exception e) {
            logger.warn("Failed to delete memory vector. userId={}, memoryId={}, error={}",
                    userId, memoryId, e.getMessage());
        }
        try {
            if (graphMemoryService != null) {
                graphMemoryService.disableMemory(userId, memoryId);
            }
        } catch (Exception e) {
            logger.warn("Failed to disable graph memory. userId={}, memoryId={}, error={}",
                    userId, memoryId, e.getMessage());
        }
    }

    public long countEnabledByUser(String userId) {
        return userMemoryRepository.countEnabledByUser(userId);
    }

    public long countEnabledByUserAndType(String userId, String memoryType) {
        return userMemoryRepository.countEnabledByUserAndType(userId, memoryType);
    }

    private UserMemory toUserMemory(String userId, String sessionId, MemoryCandidate candidate) {
        UserMemory memory = new UserMemory();
        memory.setMemoryId(UUID.randomUUID().toString());
        memory.setUserId(userId);
        memory.setSessionId(sessionId);
        memory.setAgentId("chat_agent");
        memory.setAppId(memoryProperties.getAppId());
        memory.setMemoryType(defaultText(candidate.getMemoryType(), "semantic"));
        memory.setScopeType(defaultText(candidate.getScopeType(), "user"));
        memory.setContent(candidate.getContent().trim());
        memory.setEvidence(candidate.getEvidence());
        memory.setEntities(toJson(candidate.getEntities()));
        memory.setMetadata(toJson(Map.of("reason", defaultText(candidate.getReason(), ""))));
        memory.setSource(defaultText(candidate.getSource(), "auto_extracted"));
        memory.setImportance(defaultScore(candidate.getImportance(), 0.5));
        memory.setConfidence(defaultScore(candidate.getConfidence(), 1.0));
        memory.setEvidenceScore(defaultScore(candidate.getEvidenceScore(), 0.0));
        memory.setStabilityScore(defaultScore(candidate.getStabilityScore(), 0.0));
        memory.setFutureUsefulnessScore(defaultScore(candidate.getFutureUsefulnessScore(), 0.0));
        memory.setSafetyScore(candidate.getSafetyScore() == null ? 1 : candidate.getSafetyScore());
        memory.setEnabled(true);
        return memory;
    }

    private boolean isLongTermEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.getLongTerm().isEnabled();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "null";
        }
    }

    private double defaultScore(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
