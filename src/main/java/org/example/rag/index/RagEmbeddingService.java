package org.example.rag.index;

import org.example.constant.MilvusConstants;
import org.example.rag.config.RagProperties;
import org.example.rag.model.RagChunk;
import org.example.service.VectorEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(RagEmbeddingService.class);

    private final VectorEmbeddingService vectorEmbeddingService;
    private final RagProperties ragProperties;

    public RagEmbeddingService(VectorEmbeddingService vectorEmbeddingService, RagProperties ragProperties) {
        this.vectorEmbeddingService = vectorEmbeddingService;
        this.ragProperties = ragProperties;
    }

    public Map<String, List<Float>> embedChunks(List<RagChunk> chunks) {
        List<RagChunk> indexable = chunks.stream()
                .filter(chunk -> !Boolean.TRUE.equals(chunk.getParent()))
                .toList();
        Map<String, List<Float>> vectors = new LinkedHashMap<>();
        int batchSize = Math.max(1, ragProperties.getEmbedding().getBatchSize());

        for (int from = 0; from < indexable.size(); from += batchSize) {
            int to = Math.min(indexable.size(), from + batchSize);
            List<RagChunk> batch = indexable.subList(from, to);
            logger.info("[RAG-OFFLINE] Embedding batch {}/{} size={}", from / batchSize + 1,
                    (indexable.size() + batchSize - 1) / batchSize, batch.size());
            embedBatch(batch, vectors);
        }

        long failed = indexable.stream().filter(chunk -> Boolean.TRUE.equals(chunk.getEmbeddingFailed())).count();
        if (!indexable.isEmpty() && failed > 0 && failed * 2 >= indexable.size()) {
            throw new IllegalStateException("Too many RAG chunks failed embedding: " + failed + "/" + indexable.size());
        }
        return vectors;
    }

    private void embedBatch(List<RagChunk> batch, Map<String, List<Float>> vectors) {
        List<String> contents = batch.stream()
                .map(RagChunk::getEmbeddingContent)
                .toList();
        for (int attempt = 1; attempt <= Math.max(1, ragProperties.getEmbedding().getRetryTimes()); attempt++) {
            try {
                List<List<Float>> embeddings = vectorEmbeddingService.generateEmbeddings(contents);
                if (embeddings.size() != batch.size()) {
                    throw new IllegalStateException("Embedding count mismatch: " + embeddings.size() + "/" + batch.size());
                }
                for (int i = 0; i < batch.size(); i++) {
                    List<Float> vector = embeddings.get(i);
                    validateDimension(vector);
                    vectors.put(batch.get(i).getChunkId(), vector);
                }
                return;
            } catch (Exception e) {
                logger.warn("[RAG-OFFLINE] Embedding batch failed attempt {}/{}: {}",
                        attempt, ragProperties.getEmbedding().getRetryTimes(), e.getMessage());
                if (attempt >= ragProperties.getEmbedding().getRetryTimes()) {
                    embedIndividually(batch, vectors, e);
                }
            }
        }
    }

    private void embedIndividually(List<RagChunk> batch, Map<String, List<Float>> vectors, Exception batchError) {
        for (RagChunk chunk : batch) {
            try {
                List<Float> vector = vectorEmbeddingService.generateEmbedding(chunk.getEmbeddingContent());
                validateDimension(vector);
                vectors.put(chunk.getChunkId(), vector);
            } catch (Exception e) {
                chunk.setEmbeddingFailed(true);
                chunk.setEmbeddingError(defaultText(e.getMessage(), batchError.getMessage()));
            }
        }
    }

    private void validateDimension(List<Float> vector) {
        if (vector == null || vector.size() != MilvusConstants.VECTOR_DIM) {
            throw new IllegalStateException("Unexpected embedding dimension: " + (vector == null ? 0 : vector.size()));
        }
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
