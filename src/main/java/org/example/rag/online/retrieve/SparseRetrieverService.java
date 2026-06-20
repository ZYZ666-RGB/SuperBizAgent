package org.example.rag.online.retrieve;

import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.RagChunk;
import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SparseRetrieverService {

    private final RagMetadataStoreService metadataStoreService;

    public SparseRetrieverService(RagMetadataStoreService metadataStoreService) {
        this.metadataStoreService = metadataStoreService;
    }

    public List<RetrievalCandidate> search(
            String query,
            String namespace,
            Map<String, Object> filters,
            int topK,
            QueryAnalysis analysis) {
        List<String> keywords = buildKeywords(query, analysis);
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<RagChunk> chunks = metadataStoreService.searchChunks(namespace, keywords, filters, Math.max(topK * 3, topK));
        return chunks.stream()
                .map(chunk -> toCandidate(chunk, keywords, filters))
                .sorted((a, b) -> Double.compare(score(b.getSparseScore()), score(a.getSparseScore())))
                .limit(Math.max(1, topK))
                .toList();
    }

    private List<String> buildKeywords(String query, QueryAnalysis analysis) {
        Set<String> keywords = new LinkedHashSet<>();
        if (analysis != null) {
            keywords.addAll(analysis.getErrorCodes());
            keywords.addAll(analysis.getServiceNames());
            keywords.addAll(analysis.getComponents());
            keywords.addAll(analysis.getAlertTypes());
            keywords.addAll(analysis.getKeywords());
        }
        if (query != null) {
            for (String token : query.split("[\\s,，。；;：:()（）\\[\\]{}]+")) {
                if (token.length() >= 2) {
                    keywords.add(token);
                }
            }
        }
        return keywords.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(12)
                .toList();
    }

    private RetrievalCandidate toCandidate(RagChunk chunk, List<String> keywords, Map<String, Object> filters) {
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
        candidate.setSparseScore(scoreChunk(chunk, keywords, filters));
        candidate.getMatchedBy().add("sparse");
        return candidate;
    }

    private double scoreChunk(RagChunk chunk, List<String> keywords, Map<String, Object> filters) {
        String content = defaultText(chunk.getContent(), "").toLowerCase(Locale.ROOT);
        String heading = defaultText(chunk.getHeadingPath(), "").toLowerCase(Locale.ROOT);
        String metadata = String.valueOf(chunk.getMetadata()).toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (String keyword : keywords) {
            String lower = keyword.toLowerCase(Locale.ROOT);
            score += count(content, lower);
            if (heading.contains(lower)) {
                score += 2.0;
            }
            if (metadata.contains(lower)) {
                score += 1.5;
            }
        }
        if (filters != null) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                if (entry.getValue() != null && metadata.contains(entry.getValue().toString().toLowerCase(Locale.ROOT))) {
                    score += 3.0;
                }
            }
        }
        return score;
    }

    private int count(String text, String keyword) {
        if (text.isBlank() || keyword.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) >= 0) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private double score(Double value) {
        return value == null ? 0.0 : value;
    }

    private String defaultText(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
