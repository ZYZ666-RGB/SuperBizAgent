package org.example.memory.graph;

import org.example.memory.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "memory.graph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GraphMemoryRetriever {

    private static final Logger logger = LoggerFactory.getLogger(GraphMemoryRetriever.class);

    private final MemoryProperties memoryProperties;
    private final EntityExtractionService entityExtractionService;
    private final GraphMemoryRepository graphMemoryRepository;

    public GraphMemoryRetriever(
            MemoryProperties memoryProperties,
            EntityExtractionService entityExtractionService,
            GraphMemoryRepository graphMemoryRepository) {
        this.memoryProperties = memoryProperties;
        this.entityExtractionService = entityExtractionService;
        this.graphMemoryRepository = graphMemoryRepository;
    }

    public List<String> searchRelations(String userId, String query) {
        if (!memoryProperties.isEnabled() || !memoryProperties.getGraph().isEnabled()) {
            return List.of();
        }
        try {
            List<String> entityNames = entityExtractionService.extractQueryEntityNames(query);
            return graphMemoryRepository.findRelations(
                    userId,
                    entityNames,
                    memoryProperties.getGraph().getTopK());
        } catch (Exception e) {
            logger.warn("Graph memory retrieval failed, falling back without graph context. userId={}, error={}",
                    userId, e.getMessage());
            return List.of();
        }
    }
}
