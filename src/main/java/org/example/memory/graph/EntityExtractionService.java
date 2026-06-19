package org.example.memory.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.memory.UserMemory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EntityExtractionService {

    private static final Map<String, GraphEntity> KNOWN_ENTITIES = Map.ofEntries(
            Map.entry("superbizagent", new GraphEntity("SuperBizAgent", "Project")),
            Map.entry("mysql", new GraphEntity("MySQL", "Technology")),
            Map.entry("milvus", new GraphEntity("Milvus", "Technology")),
            Map.entry("neo4j", new GraphEntity("Neo4j", "Technology")),
            Map.entry("spring ai alibaba", new GraphEntity("Spring AI Alibaba", "Technology")),
            Map.entry("spring boot", new GraphEntity("Spring Boot", "Technology")),
            Map.entry("dashscope", new GraphEntity("DashScope", "Technology")),
            Map.entry("prometheus", new GraphEntity("Prometheus", "Technology")),
            Map.entry("rag", new GraphEntity("RAG", "Module")),
            Map.entry("aiops", new GraphEntity("AIOps", "Module")),
            Map.entry("reactagent", new GraphEntity("ReactAgent", "Agent")),
            Map.entry("supervisor", new GraphEntity("SupervisorAgent", "Agent")),
            Map.entry("planner", new GraphEntity("Planner", "Agent")),
            Map.entry("executor", new GraphEntity("Executor", "Agent"))
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<GraphEntity> extractEntities(UserMemory memory) {
        LinkedHashMap<String, GraphEntity> entities = new LinkedHashMap<>();
        String content = defaultText(memory.getContent());

        addKnownEntities(content, entities);
        addJsonEntities(memory, entities);
        addMemoryTypeEntity(memory, entities);

        return new ArrayList<>(entities.values());
    }

    public List<String> extractQueryEntityNames(String query) {
        LinkedHashMap<String, GraphEntity> entities = new LinkedHashMap<>();
        addKnownEntities(defaultText(query), entities);
        return entities.values().stream()
                .map(GraphEntity::getName)
                .toList();
    }

    private void addKnownEntities(String content, LinkedHashMap<String, GraphEntity> entities) {
        String normalized = content.toLowerCase(Locale.ROOT);
        KNOWN_ENTITIES.forEach((keyword, entity) -> {
            if (normalized.contains(keyword)) {
                entities.putIfAbsent(entity.key(), entity);
            }
        });
    }

    private void addJsonEntities(UserMemory memory, LinkedHashMap<String, GraphEntity> entities) {
        String raw = memory.getEntities();
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return;
        }
        try {
            List<String> names = objectMapper.readValue(raw, new TypeReference<>() {
            });
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                GraphEntity entity = classify(name, memory.getMemoryType());
                entities.putIfAbsent(entity.key(), entity);
            }
        } catch (Exception ignored) {
            GraphEntity entity = classify(raw, memory.getMemoryType());
            entities.putIfAbsent(entity.key(), entity);
        }
    }

    private void addMemoryTypeEntity(UserMemory memory, LinkedHashMap<String, GraphEntity> entities) {
        String memoryType = defaultText(memory.getMemoryType());
        String content = defaultText(memory.getContent());
        if (content.isBlank()) {
            return;
        }
        if ("preference".equals(memoryType)) {
            GraphEntity entity = new GraphEntity(truncate(content, 80), "Preference");
            entities.putIfAbsent(entity.key(), entity);
        } else if ("career_goal".equals(memoryType)) {
            GraphEntity entity = new GraphEntity(truncate(content, 80), "Goal");
            entities.putIfAbsent(entity.key(), entity);
        } else if ("task".equals(memoryType)) {
            GraphEntity entity = new GraphEntity(truncate(content, 80), "Task");
            entities.putIfAbsent(entity.key(), entity);
        }
    }

    private GraphEntity classify(String rawName, String memoryType) {
        String name = normalizeName(rawName);
        String normalized = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, GraphEntity> entry : KNOWN_ENTITIES.entrySet()) {
            if (normalized.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (normalized.contains("project") || "project_context".equals(memoryType)) {
            return new GraphEntity(name, "Project");
        }
        if (normalized.contains("agent")) {
            return new GraphEntity(name, "Agent");
        }
        if (normalized.contains("tool")) {
            return new GraphEntity(name, "Tool");
        }
        return new GraphEntity(name, "Technology");
    }

    private String normalizeName(String rawName) {
        return rawName == null ? "" : rawName.trim()
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}
