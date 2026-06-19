package org.example.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.memory.graph.GraphMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EpisodicMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(EpisodicMemoryService.class);

    private final MemoryProperties memoryProperties;
    private final UserMemoryRepository userMemoryRepository;
    private final MemoryVectorService memoryVectorService;
    private final MemorySafetyService memorySafetyService;
    private final GraphMemoryService graphMemoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public EpisodicMemoryService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            MemoryVectorService memoryVectorService,
            MemorySafetyService memorySafetyService,
            ObjectProvider<GraphMemoryService> graphMemoryServiceProvider) {
        this(memoryProperties, userMemoryRepository, memoryVectorService,
                memorySafetyService, graphMemoryServiceProvider.getIfAvailable());
    }

    public EpisodicMemoryService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            MemoryVectorService memoryVectorService,
            MemorySafetyService memorySafetyService,
            GraphMemoryService graphMemoryService) {
        this.memoryProperties = memoryProperties;
        this.userMemoryRepository = userMemoryRepository;
        this.memoryVectorService = memoryVectorService;
        this.memorySafetyService = memorySafetyService;
        this.graphMemoryService = graphMemoryService;
    }

    public UserMemory saveEvent(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String eventType,
            String content,
            Map<String, Object> metadata,
            double importance) {
        if (!isEnabled() || content == null || content.isBlank()) {
            return null;
        }

        UserMemory memory = buildMemory(
                userId,
                sessionId,
                taskId,
                agentId,
                eventType,
                content,
                metadata,
                importance);
        if (!memorySafetyService.isTextSafe(memory.getContent())
                || !memorySafetyService.isTextSafe(memory.getMetadata())) {
            logger.warn("Skipped unsafe episodic memory. userId={}, taskId={}, eventType={}",
                    userId, taskId, eventType);
            return null;
        }
        userMemoryRepository.insert(memory);
        indexVector(memory);
        indexGraph(memory);
        logger.info("Saved episodic memory. userId={}, taskId={}, eventType={}, memoryId={}",
                userId, taskId, eventType, memory.getMemoryId());
        return memory;
    }

    private UserMemory buildMemory(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String eventType,
            String content,
            Map<String, Object> metadata,
            double importance) {
        UserMemory memory = new UserMemory();
        memory.setMemoryId(UUID.randomUUID().toString());
        memory.setUserId(defaultText(userId, "default_user"));
        memory.setSessionId(emptyToNull(sessionId));
        memory.setTaskId(emptyToNull(taskId));
        memory.setAgentId(defaultText(agentId, "event_agent"));
        memory.setAppId(memoryProperties.getAppId());
        memory.setMemoryType("episodic");
        memory.setScopeType(scopeType(sessionId, taskId));
        memory.setContent(content.trim());
        memory.setEvidence(content.trim());
        memory.setEntities("[]");
        memory.setMetadata(toMetadataJson(eventType, taskId, agentId, metadata));
        memory.setSource("event");
        memory.setImportance(clamp(importance));
        memory.setConfidence(1.0);
        memory.setEvidenceScore(1.0);
        memory.setStabilityScore(0.8);
        memory.setFutureUsefulnessScore(0.75);
        memory.setSafetyScore(1);
        memory.setEnabled(true);
        return memory;
    }

    private void indexVector(UserMemory memory) {
        try {
            memoryVectorService.indexMemory(memory);
        } catch (Exception e) {
            logger.warn("Failed to index episodic memory vector. memoryId={}, error={}",
                    memory.getMemoryId(), e.getMessage());
        }
    }

    private void indexGraph(UserMemory memory) {
        try {
            if (graphMemoryService != null) {
                graphMemoryService.indexMemory(memory);
            }
        } catch (Exception e) {
            logger.warn("Failed to index episodic graph memory. memoryId={}, error={}",
                    memory.getMemoryId(), e.getMessage());
        }
    }

    private String toMetadataJson(
            String eventType,
            String taskId,
            String agentId,
            Map<String, Object> metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("eventType", defaultText(eventType, "event"));
        values.put("taskId", emptyToNull(taskId));
        values.put("agentId", defaultText(agentId, "event_agent"));
        values.put("timestamp", LocalDateTime.now().toString());
        if (metadata != null) {
            values.putAll(metadata);
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String scopeType(String sessionId, String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            return "task";
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return "session";
        }
        return "user";
    }

    private boolean isEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.getLongTerm().isEnabled();
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
