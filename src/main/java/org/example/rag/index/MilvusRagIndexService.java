package org.example.rag.index;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import org.example.constant.MilvusConstants;
import org.example.rag.config.RagProperties;
import org.example.rag.model.RagChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MilvusRagIndexService {

    private static final Logger logger = LoggerFactory.getLogger(MilvusRagIndexService.class);

    private final MilvusServiceClient milvusClient;
    private final RagProperties ragProperties;
    private final Gson gson = new Gson();

    public MilvusRagIndexService(MilvusServiceClient milvusClient, RagProperties ragProperties) {
        this.milvusClient = milvusClient;
        this.ragProperties = ragProperties;
    }

    public void upsertChunks(String documentId, List<RagChunk> chunks, Map<String, List<Float>> vectors) {
        if (ragProperties.getIndex().isDeleteOldBeforeReindex()) {
            deleteByDocumentId(documentId);
        }
        loadCollection();
        for (RagChunk chunk : chunks) {
            if (Boolean.TRUE.equals(chunk.getParent()) || Boolean.TRUE.equals(chunk.getEmbeddingFailed())) {
                continue;
            }
            List<Float> vector = vectors.get(chunk.getChunkId());
            if (vector == null) {
                continue;
            }
            insertChunk(chunk, vector);
        }
        logger.info("[RAG-OFFLINE] Milvus write completed. documentId={}, chunks={}", documentId, vectors.size());
    }

    public void deleteByDocumentId(String documentId) {
        try {
            loadCollection();
            String expr = "metadata[\"documentId\"] == \"" + escapeExpressionString(documentId) + "\"";
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();
            R<MutationResult> response = milvusClient.delete(deleteParam);
            if (response.getStatus() != 0) {
                logger.warn("[RAG-OFFLINE] Failed to delete old Milvus vectors: {}", response.getMessage());
            }
        } catch (Exception e) {
            logger.warn("[RAG-OFFLINE] Delete old Milvus vectors skipped: {}", e.getMessage());
        }
    }

    private void insertChunk(RagChunk chunk, List<Float> vector) {
        String content = defaultText(chunk.getContent(), "");
        String milvusContent = truncateToUtf8Bytes(content, MilvusConstants.CONTENT_MAX_LENGTH);
        if (!milvusContent.equals(content)) {
            logger.warn("[RAG-OFFLINE] Chunk content truncated before Milvus insert. chunkId={}, chars={} -> {}, bytes={} -> {}",
                    chunk.getChunkId(),
                    content.length(),
                    milvusContent.length(),
                    utf8Length(content),
                    utf8Length(milvusContent));
        }

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", List.of(chunk.getChunkId())));
        fields.add(new InsertParam.Field("content", List.of(milvusContent)));
        fields.add(new InsertParam.Field("vector", List.of(vector)));
        JsonObject metadata = gson.toJsonTree(chunk.getMetadata()).getAsJsonObject();
        fields.add(new InsertParam.Field("metadata", List.of(metadata)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFields(fields)
                .build();
        R<MutationResult> response = milvusClient.insert(insertParam);
        if (response.getStatus() != 0) {
            throw new IllegalStateException("Failed to insert RAG chunk vector: " + response.getMessage());
        }
    }

    private void loadCollection() {
        R<RpcStatus> loadResponse = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                        .build());
        if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
            throw new IllegalStateException("Load biz collection failed: " + loadResponse.getMessage());
        }
    }

    private String escapeExpressionString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String truncateToUtf8Bytes(String value, int maxBytes) {
        if (value == null || value.isEmpty() || maxBytes <= 0) {
            return "";
        }
        if (utf8Length(value) <= maxBytes) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        int bytes = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            String codePointText = new String(Character.toChars(codePoint));
            int codePointBytes = utf8Length(codePointText);
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            builder.appendCodePoint(codePoint);
            bytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
