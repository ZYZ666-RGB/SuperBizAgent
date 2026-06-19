package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.memory.LongTermMemoryService;
import org.example.memory.MemoryConsolidationService;
import org.example.memory.MemoryForgettingService;
import org.example.memory.MemoryUserContext;
import org.example.memory.UserMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MemoryTools {

    private static final Logger logger = LoggerFactory.getLogger(MemoryTools.class);

    private final LongTermMemoryService longTermMemoryService;
    private final MemoryConsolidationService memoryConsolidationService;
    private final MemoryForgettingService memoryForgettingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MemoryTools(
            LongTermMemoryService longTermMemoryService,
            MemoryConsolidationService memoryConsolidationService,
            MemoryForgettingService memoryForgettingService) {
        this.longTermMemoryService = longTermMemoryService;
        this.memoryConsolidationService = memoryConsolidationService;
        this.memoryForgettingService = memoryForgettingService;
    }

    @Tool(description = "Search the current user's scoped memories. Never returns memories from other users.")
    public String searchMemory(
            @ToolParam(description = "Search query for user memories") String query,
            @ToolParam(description = "Optional memory type such as semantic, episodic, preference, project_context, task") String memoryType,
            @ToolParam(description = "Optional max result count, default 5, max 20") Integer limit) {
        String userId = MemoryUserContext.getUserId();
        List<UserMemory> memories = longTermMemoryService.searchMemories(
                userId,
                query,
                memoryType,
                normalizeLimit(limit));
        return toJson(Map.of(
                "status", "ok",
                "count", memories.size(),
                "memories", memories.stream().map(this::toToolMemory).toList()));
    }

    @Tool(description = "Add a memory for the current user after safety filtering.")
    public String addMemory(
            @ToolParam(description = "Memory content to save") String content,
            @ToolParam(description = "Memory type such as semantic, preference, project_context, episodic or task") String memoryType,
            @ToolParam(description = "Importance from 0.0 to 1.0") Double importance) {
        String userId = MemoryUserContext.getUserId();
        List<UserMemory> saved = longTermMemoryService.addManualMemory(
                userId,
                content,
                defaultText(memoryType, "semantic"),
                "user",
                importance == null ? 0.75 : importance);
        return toJson(Map.of(
                "status", saved.isEmpty() ? "skipped" : "ok",
                "count", saved.size(),
                "memories", saved.stream().map(this::toToolMemory).toList()));
    }

    @Tool(description = "Update one current-user memory by memoryId. Unsafe content is rejected.")
    public String updateMemory(
            @ToolParam(description = "Memory id returned by searchMemory or addMemory") String memoryId,
            @ToolParam(description = "New memory content") String newContent) {
        String userId = MemoryUserContext.getUserId();
        boolean updated = longTermMemoryService.updateMemory(userId, memoryId, newContent);
        return toJson(Map.of("status", updated ? "ok" : "not_found_or_rejected"));
    }

    @Tool(description = "Disable one current-user memory by memoryId.")
    public String removeMemory(
            @ToolParam(description = "Memory id returned by searchMemory or addMemory") String memoryId) {
        String userId = MemoryUserContext.getUserId();
        boolean removed = longTermMemoryService.removeMemory(userId, memoryId);
        return toJson(Map.of("status", removed ? "ok" : "not_found"));
    }

    @Tool(description = "Consolidate high-value episodic memories into semantic memory for the current user.")
    public String consolidateMemory() {
        String userId = MemoryUserContext.getUserId();
        List<UserMemory> saved = memoryConsolidationService.consolidateUser(userId);
        return toJson(Map.of(
                "status", saved.isEmpty() ? "no_candidates" : "ok",
                "count", saved.size(),
                "memories", saved.stream().map(this::toToolMemory).toList()));
    }

    @Tool(description = "Forget low-value, stale, or over-capacity memories for the current user.")
    public String forgetMemory(
            @ToolParam(description = "Strategy: importance_based, time_based, or capacity_based") String strategy) {
        String userId = MemoryUserContext.getUserId();
        int disabled = memoryForgettingService.forget(userId, strategy);
        return toJson(Map.of("status", "ok", "disabledCount", disabled));
    }

    @Tool(description = "Get memory statistics for the current user.")
    public String memoryStats() {
        String userId = MemoryUserContext.getUserId();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("status", "ok");
        stats.put("totalEnabled", longTermMemoryService.countEnabledByUser(userId));
        stats.put("semantic", longTermMemoryService.countEnabledByUserAndType(userId, "semantic"));
        stats.put("episodic", longTermMemoryService.countEnabledByUserAndType(userId, "episodic"));
        stats.put("preference", longTermMemoryService.countEnabledByUserAndType(userId, "preference"));
        stats.put("projectContext", longTermMemoryService.countEnabledByUserAndType(userId, "project_context"));
        return toJson(stats);
    }

    private Map<String, Object> toToolMemory(UserMemory memory) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("memoryId", memory.getMemoryId());
        value.put("memoryType", memory.getMemoryType());
        value.put("scopeType", memory.getScopeType());
        value.put("content", memory.getContent());
        value.put("importance", memory.getImportance());
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.warn("Failed to serialize memory tool response", e);
            return "{\"status\":\"error\",\"message\":\"serialization failed\"}";
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }
        return Math.max(1, Math.min(20, limit));
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
