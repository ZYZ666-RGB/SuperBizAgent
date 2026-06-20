package org.example.rag.online.retrieve;

import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetrievalDedupService {

    public List<RetrievalCandidate> dedup(List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : candidates) {
            if (candidate == null || candidate.getChunkId() == null || candidate.getChunkId().isBlank()) {
                continue;
            }
            merged.merge(candidate.getChunkId(), candidate, this::mergeCandidate);
        }
        return new ArrayList<>(merged.values());
    }

    RetrievalCandidate mergeCandidate(RetrievalCandidate left, RetrievalCandidate right) {
        left.setDenseScore(max(left.getDenseScore(), right.getDenseScore()));
        left.setSparseScore(max(left.getSparseScore(), right.getSparseScore()));
        left.setFusedScore(max(left.getFusedScore(), right.getFusedScore()));
        left.setRerankScore(max(left.getRerankScore(), right.getRerankScore()));
        for (String matchedBy : right.getMatchedBy()) {
            if (!left.getMatchedBy().contains(matchedBy)) {
                left.getMatchedBy().add(matchedBy);
            }
        }
        if ((left.getContent() == null || left.getContent().isBlank()) && right.getContent() != null) {
            left.setContent(right.getContent());
        }
        if ((left.getEmbeddingContent() == null || left.getEmbeddingContent().isBlank()) && right.getEmbeddingContent() != null) {
            left.setEmbeddingContent(right.getEmbeddingContent());
        }
        if (left.getMetadata().isEmpty() && right.getMetadata() != null) {
            left.setMetadata(right.getMetadata());
        }
        return left;
    }

    private Double max(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }
}
