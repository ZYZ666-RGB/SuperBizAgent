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

    public long countEnabledByUser(String userId) {
        return userMemoryRepository.countEnabledByUser(userId);
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
