package org.example.rag.online.retrieve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.RagChunk;
import org.example.rag.online.model.RetrievalCandidate;
import org.example.service.VectorEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DenseRetrieverService {

    private static final Logger logger = LoggerFactory.getLogger(DenseRetrieverService.class);

    private final MilvusServiceClient milvusClient;
    private final VectorEmbeddingService embeddingService;
    private final RagMetadataStoreService metadataStoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DenseRetrieverService(
            MilvusServiceClient milvusClient,
            VectorEmbeddingService embeddingService,
            RagMetadataStoreService metadataStoreService) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
        this.metadataStoreService = metadataStoreService;
    }

    public List<RetrievalCandidate> search(
            String query,
            String namespace,
            Map<String, Object> filters,
            int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            SearchParam.Builder builder = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(Math.max(1, topK))
                    .withMetricType(MetricType.L2)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":10}");
            String expr = buildExpr(namespace, filters);
            if (!expr.isBlank()) {
                builder.withExpr(expr);
            }

            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build());
            R<SearchResults> response = milvusClient.search(builder.build());
            if (response.getStatus() != 0) {
                logger.warn("[RAG-ONLINE] Dense search failed: {}", response.getMessage());
                return List.of();
            }
            return parseResults(response.getData(), query);
        } catch (Exception e) {
            logger.warn("[RAG-ONLINE] Dense search skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RetrievalCandidate> parseResults(SearchResults searchResults, String query) {
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getResults());
        List<RetrievalCandidate> candidates = new ArrayList<>();
        int rows = wrapper.getRowRecords(0).size();
        for (int i = 0; i < rows; i++) {
            int row = i;
            String chunkId = (String) wrapper.getIDScore(0).get(i).get("id");
            double score = wrapper.getIDScore(0).get(i).getScore();
            RetrievalCandidate candidate = hydrateFromStore(chunkId)
                    .orElseGet(() -> fromMilvusFields(wrapper, row, chunkId));
            candidate.setDenseScore(score);
            candidate.getMatchedBy().add(query.equals(candidate.getContent()) ? "dense" : "dense");
            candidates.add(candidate);
        }
        return candidates;
    }

    private Optional<RetrievalCandidate> hydrateFromStore(String chunkId) {
        return metadataStoreService.findChunkById(chunkId).map(this::fromChunk);
    }

    private RetrievalCandidate fromChunk(RagChunk chunk) {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId(chunk.getChunkId());
        candidate.setDocumentId(chunk.getDocumentId());
        candidate.setParentChunkId(chunk.getParentChunkId());
        candidate.setNamespace(chunk.getNamespace());
        candidate.setFileName(chunk.getFileName());
        candidate.setFileType(chunk.getFileType());
        candidate.setSourcePath(chunk.getSourcePath());
        candidate.setHeadingPath(chunk.getHeadingPath());
        candidate.setChunkIndex(chunk.getChunkIndex());
        candidate.setContent(chunk.getContent());
        candidate.setEmbeddingContent(chunk.getEmbeddingContent());
        candidate.setMetadata(chunk.getMetadata());
        return candidate;
    }

    private RetrievalCandidate fromMilvusFields(SearchResultsWrapper wrapper, int index, String chunkId) {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId(chunkId);
        candidate.setContent((String) wrapper.getFieldData("content", 0).get(index));
        Object metadataObject = wrapper.getFieldData("metadata", 0).get(index);
        Map<String, Object> metadata = parseMetadata(metadataObject);
        candidate.setMetadata(metadata);
        candidate.setDocumentId(asString(metadata.get("documentId")));
        candidate.setParentChunkId(asString(metadata.get("parentChunkId")));
        candidate.setNamespace(asString(metadata.get("namespace")));
        candidate.setFileName(asString(metadata.get("fileName")));
        candidate.setFileType(asString(metadata.get("fileType")));
        candidate.setSourcePath(asString(metadata.get("sourcePath")));
        candidate.setHeadingPath(asString(metadata.get("headingPath")));
        candidate.setChunkIndex(asInteger(metadata.get("chunkIndex")));
        return candidate;
    }

    private Map<String, Object> parseMetadata(Object metadataObject) {
        if (metadataObject == null) {
            return new LinkedHashMap<>();
        }
        if (metadataObject instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        try {
            return objectMapper.readValue(metadataObject.toString(), new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String buildExpr(String namespace, Map<String, Object> filters) {
        List<String> clauses = new ArrayList<>();
        if (namespace != null && !namespace.isBlank()) {
            clauses.add("metadata[\"namespace\"] == \"" + escape(namespace) + "\"");
        }
        if (filters != null) {
            filters.forEach((key, value) -> {
                if (value != null && !value.toString().isBlank()) {
                    clauses.add("metadata[\"" + escape(key) + "\"] == \"" + escape(value.toString()) + "\"");
                }
            });
        }
        return String.join(" and ", clauses);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
