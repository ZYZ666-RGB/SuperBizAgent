package org.example.memory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.example.service.VectorEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryVectorService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryVectorService.class);

    private static final List<String> DEFAULT_MEMORY_TYPES = List.of(
            "semantic",
            "preference",
            "project_context",
            "career_goal",
            "skill",
            "environment",
            "task"
    );

    private final MilvusServiceClient milvusClient;
    private final VectorEmbeddingService embeddingService;
    private final MemoryProperties memoryProperties;
    private final Gson gson = new Gson();

    public MemoryVectorService(
            MilvusServiceClient milvusClient,
            VectorEmbeddingService embeddingService,
            MemoryProperties memoryProperties) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
        this.memoryProperties = memoryProperties;
    }

    public void indexMemory(UserMemory memory) {
        if (!isEnabled() || memory == null || memory.getContent() == null || memory.getContent().isBlank()) {
            return;
        }

        try {
            List<Float> vector = embeddingService.generateEmbedding(memory.getContent());
            if (vector.size() != memoryProperties.getVector().getDimension()) {
                throw new IllegalStateException("Unexpected memory embedding dimension: " + vector.size());
            }

            loadCollection();
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName())
                    .withFields(buildInsertFields(memory, vector))
                    .build();

            R<MutationResult> response = milvusClient.insert(insertParam);
            if (response.getStatus() != 0) {
                throw new RuntimeException("Insert user memory vector failed: " + response.getMessage());
            }
            logger.info("Indexed user memory vector. memoryId={}", memory.getMemoryId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to index user memory vector: " + e.getMessage(), e);
        }
    }

    public List<UserMemory> searchMemories(String userId, String query, List<String> memoryTypes, int limit) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            loadCollection();

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName())
                    .withVectorFieldName("vector")
                    .withVectors(List.of(queryVector))
                    .withTopK(Math.max(1, limit))
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(List.of(
                            "memory_id", "user_id", "session_id", "task_id", "agent_id", "app_id",
                            "memory_type", "scope_type", "content", "importance", "metadata",
                            "created_at", "enabled"
                    ))
                    .withExpr(buildSearchExpression(userId, memoryTypes))
                    .withParams("{\"nprobe\":10}")
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);
            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("Search user memory vector failed: " + searchResponse.getMessage());
            }

            return parseSearchResults(searchResponse, limit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to search user memory vectors: " + e.getMessage(), e);
        }
    }

    String buildSearchExpression(String userId, List<String> memoryTypes) {
        List<String> types = memoryTypes == null || memoryTypes.isEmpty() ? DEFAULT_MEMORY_TYPES : memoryTypes;
        String quotedTypes = types.stream()
                .map(this::escapeExpressionString)
                .map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("\"semantic\"");
        return "user_id == \"" + escapeExpressionString(userId) + "\""
                + " && enabled == true"
                + " && memory_type in [" + quotedTypes + "]";
    }

    private List<InsertParam.Field> buildInsertFields(UserMemory memory, List<Float> vector) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", defaultText(memory.getSource(), ""));
        metadata.put("evidence", defaultText(memory.getEvidence(), ""));
        metadata.put("metadata", defaultText(memory.getMetadata(), "{}"));
        metadata.put("entities", defaultText(memory.getEntities(), "[]"));

        JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("memory_id", List.of(defaultText(memory.getMemoryId(), ""))));
        fields.add(new InsertParam.Field("user_id", List.of(defaultText(memory.getUserId(), ""))));
        fields.add(new InsertParam.Field("session_id", List.of(defaultText(memory.getSessionId(), ""))));
        fields.add(new InsertParam.Field("task_id", List.of(defaultText(memory.getTaskId(), ""))));
        fields.add(new InsertParam.Field("agent_id", List.of(defaultText(memory.getAgentId(), ""))));
        fields.add(new InsertParam.Field("app_id", List.of(defaultText(memory.getAppId(), "super_biz_agent"))));
        fields.add(new InsertParam.Field("memory_type", List.of(defaultText(memory.getMemoryType(), "semantic"))));
        fields.add(new InsertParam.Field("scope_type", List.of(defaultText(memory.getScopeType(), "user"))));
        fields.add(new InsertParam.Field("content", List.of(truncate(defaultText(memory.getContent(), ""), MilvusConstants.CONTENT_MAX_LENGTH))));
        fields.add(new InsertParam.Field("importance", List.of(memory.getImportance() == null ? 0.5 : memory.getImportance())));
        fields.add(new InsertParam.Field("metadata", List.of(metadataJson)));
        fields.add(new InsertParam.Field("created_at", List.of(LocalDateTime.now().toString())));
        fields.add(new InsertParam.Field("enabled", List.of(memory.getEnabled() == null || memory.getEnabled())));
        fields.add(new InsertParam.Field("vector", List.of(vector)));
        return fields;
    }

    private List<UserMemory> parseSearchResults(R<SearchResults> searchResponse, int limit) {
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
        List<ScoredMemory> scoredMemories = new ArrayList<>();
        int rowCount = wrapper.getRowRecords(0).size();
        for (int i = 0; i < rowCount; i++) {
            UserMemory memory = new UserMemory();
            memory.setMemoryId(stringField(wrapper, "memory_id", i));
            memory.setUserId(stringField(wrapper, "user_id", i));
            memory.setSessionId(stringField(wrapper, "session_id", i));
            memory.setTaskId(stringField(wrapper, "task_id", i));
            memory.setAgentId(stringField(wrapper, "agent_id", i));
            memory.setAppId(stringField(wrapper, "app_id", i));
            memory.setMemoryType(stringField(wrapper, "memory_type", i));
            memory.setScopeType(stringField(wrapper, "scope_type", i));
            memory.setContent(stringField(wrapper, "content", i));
            memory.setImportance(doubleField(wrapper, "importance", i, 0.5));
            memory.setMetadata(stringField(wrapper, "metadata", i));
            memory.setEnabled(booleanField(wrapper, "enabled", i, true));

            float vectorScore = wrapper.getIDScore(0).get(i).getScore();
            double finalScore = vectorScore * (0.8 + memory.getImportance() * 0.4);
            scoredMemories.add(new ScoredMemory(memory, finalScore));
        }

        return scoredMemories.stream()
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .limit(Math.max(1, limit))
                .map(ScoredMemory::memory)
                .toList();
    }

    private void loadCollection() {
        R<RpcStatus> loadResponse = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName())
                        .build()
        );
        if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
            throw new RuntimeException("Load user memory collection failed: " + loadResponse.getMessage());
        }
    }

    private String stringField(SearchResultsWrapper wrapper, String fieldName, int index) {
        Object value = wrapper.getFieldData(fieldName, 0).get(index);
        return value == null ? null : value.toString();
    }

    private double doubleField(SearchResultsWrapper wrapper, String fieldName, int index, double defaultValue) {
        Object value = wrapper.getFieldData(fieldName, 0).get(index);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private boolean booleanField(SearchResultsWrapper wrapper, String fieldName, int index, boolean defaultValue) {
        Object value = wrapper.getFieldData(fieldName, 0).get(index);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    private boolean isEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.getVector().isEnabled();
    }

    private String collectionName() {
        String configured = memoryProperties.getVector().getCollectionName();
        if (configured == null || configured.isBlank()) {
            return MilvusConstants.USER_MEMORY_COLLECTION_NAME;
        }
        return configured;
    }

    private String escapeExpressionString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ScoredMemory(UserMemory memory, double score) {
    }
}
