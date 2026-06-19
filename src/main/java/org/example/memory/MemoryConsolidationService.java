package org.example.memory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemoryConsolidationService {

    private final MemoryProperties memoryProperties;
    private final UserMemoryRepository userMemoryRepository;
    private final LongTermMemoryService longTermMemoryService;

    public MemoryConsolidationService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            LongTermMemoryService longTermMemoryService) {
        this.memoryProperties = memoryProperties;
        this.userMemoryRepository = userMemoryRepository;
        this.longTermMemoryService = longTermMemoryService;
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void consolidateMemories() {
        if (!memoryProperties.isEnabled() || !memoryProperties.getConsolidation().isEnabled()) {
            return;
        }
        for (String userId : userMemoryRepository.findUserIdsWithEnabledMemories(1000)) {
            consolidateUser(userId);
        }
    }

    public List<UserMemory> consolidateUser(String userId) {
        if (!memoryProperties.isEnabled() || !memoryProperties.getConsolidation().isEnabled()) {
            return List.of();
        }
        List<UserMemory> sourceMemories = userMemoryRepository.findHighImportanceEpisodic(
                userId,
                memoryProperties.getConsolidation().getImportanceThreshold(),
                memoryProperties.getConsolidation().getMaxSourceMemories());
        if (sourceMemories.isEmpty()) {
            return List.of();
        }

        String content = "Consolidated stable user/project context from episodic events: "
                + sourceMemories.stream()
                .map(UserMemory::getContent)
                .collect(Collectors.joining(" | "));
        double importance = sourceMemories.stream()
                .map(UserMemory::getImportance)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.7);
        return longTermMemoryService.addManualMemory(
                userId,
                content,
                "semantic",
                "user",
                Math.max(0.7, importance));
    }
}
