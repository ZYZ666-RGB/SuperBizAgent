package org.example.memory.graph;

import org.example.memory.MemoryProperties;
import org.example.memory.UserMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "memory.graph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GraphMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(GraphMemoryService.class);

    private static final Set<String> GRAPH_MEMORY_TYPES = Set.of(
            "semantic", "project_context", "preference", "skill",
            "career_goal", "task", "episodic");

    private final MemoryProperties memoryProperties;
    private final EntityExtractionService entityExtractionService;
    private final RelationExtractionService relationExtractionService;
    private final GraphMemoryRepository graphMemoryRepository;

    public GraphMemoryService(
            MemoryProperties memoryProperties,
            EntityExtractionService entityExtractionService,
            RelationExtractionService relationExtractionService,
            GraphMemoryRepository graphMemoryRepository) {
        this.memoryProperties = memoryProperties;
        this.entityExtractionService = entityExtractionService;
        this.relationExtractionService = relationExtractionService;
        this.graphMemoryRepository = graphMemoryRepository;
    }

    public void indexMemory(UserMemory memory) {
        if (!shouldIndex(memory)) {
            return;
        }
        List<GraphEntity> entities = entityExtractionService.extractEntities(memory);
        if (entities.isEmpty()) {
            return;
        }
        GraphExtractionResult extraction = new GraphExtractionResult();
        extraction.setEntities(entities);
        extraction.setRelations(relationExtractionService.extractRelations(memory, entities));
        graphMemoryRepository.upsertMemory(memory, extraction);
        logger.info("Indexed graph memory. userId={}, memoryId={}, entities={}",
                memory.getUserId(), memory.getMemoryId(), entities.size());
    }

    public void disableMemory(String userId, String memoryId) {
        if (!memoryProperties.isEnabled() || !memoryProperties.getGraph().isEnabled()) {
            return;
        }
        graphMemoryRepository.disableMemory(userId, memoryId);
    }

    boolean shouldIndex(UserMemory memory) {
        if (!memoryProperties.isEnabled() || !memoryProperties.getGraph().isEnabled() || memory == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(memory.getEnabled())) {
            return false;
        }
        if (!GRAPH_MEMORY_TYPES.contains(memory.getMemoryType())) {
            return false;
        }
        return !"episodic".equals(memory.getMemoryType())
                || memory.getImportance() == null
                || memory.getImportance() >= 0.7;
    }
}
