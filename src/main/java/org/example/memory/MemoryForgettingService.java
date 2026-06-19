package org.example.memory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemoryForgettingService {

    private final MemoryProperties memoryProperties;
    private final UserMemoryRepository userMemoryRepository;
    private final LongTermMemoryService longTermMemoryService;

    public MemoryForgettingService(
            MemoryProperties memoryProperties,
            UserMemoryRepository userMemoryRepository,
            LongTermMemoryService longTermMemoryService) {
        this.memoryProperties = memoryProperties;
        this.userMemoryRepository = userMemoryRepository;
        this.longTermMemoryService = longTermMemoryService;
    }

    @Scheduled(cron = "0 15/30 * * * ?")
    public void forgetLowValueMemories() {
        if (!memoryProperties.isEnabled() || !memoryProperties.getForgetting().isEnabled()) {
            return;
        }
        for (String userId : userMemoryRepository.findUserIdsWithEnabledMemories(1000)) {
            forget(userId, "importance_based");
            forget(userId, "time_based");
            forget(userId, "capacity_based");
        }
    }

    public int forget(String userId, String strategy) {
        if (!memoryProperties.isEnabled() || !memoryProperties.getForgetting().isEnabled()) {
            return 0;
        }
        String normalized = strategy == null || strategy.isBlank() ? "importance_based" : strategy.trim();
        return switch (normalized) {
            case "time_based" -> forgetByTime(userId);
            case "capacity_based" -> forgetByCapacity(userId);
            case "importance_based" -> forgetByImportance(userId);
            default -> forgetByImportance(userId);
        };
    }

    private int forgetByImportance(String userId) {
        List<UserMemory> candidates = userMemoryRepository.findLowValueMemories(
                userId,
                memoryProperties.getForgetting().getMinImportance(),
                100);
        return disable(userId, candidates);
    }

    private int forgetByTime(String userId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(memoryProperties.getForgetting().getMaxAgeDays());
        List<UserMemory> candidates = userMemoryRepository.findStaleEpisodicMemories(
                userId,
                cutoff,
                0.4,
                100);
        return disable(userId, candidates);
    }

    private int forgetByCapacity(String userId) {
        long enabledCount = userMemoryRepository.countEnabledByUser(userId);
        int max = memoryProperties.getForgetting().getMaxMemoriesPerUser();
        if (enabledCount <= max) {
            return 0;
        }
        int overflow = (int) Math.min(Integer.MAX_VALUE, enabledCount - max);
        List<UserMemory> candidates = userMemoryRepository.findCapacityOverflowCandidates(userId, max, overflow);
        return disable(userId, candidates);
    }

    private int disable(String userId, List<UserMemory> candidates) {
        int count = 0;
        for (UserMemory memory : candidates) {
            longTermMemoryService.disableMemory(userId, memory.getMemoryId());
            count++;
        }
        return count;
    }
}
