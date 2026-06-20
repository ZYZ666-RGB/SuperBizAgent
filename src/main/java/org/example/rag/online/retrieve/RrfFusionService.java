package org.example.rag.online.retrieve;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RrfFusionService {

    private final RagOnlineProperties properties;
    private final RetrievalDedupService dedupService;

    public RrfFusionService(RagOnlineProperties properties, RetrievalDedupService dedupService) {
        this.properties = properties;
        this.dedupService = dedupService;
    }

    public List<RetrievalCandidate> fuse(List<List<RetrievalCandidate>> rankedLists, int topK) {
        if (rankedLists == null || rankedLists.isEmpty()) {
            return List.of();
        }
        int rrfK = Math.max(1, properties.getFusion().getRrfK());
        Map<String, RetrievalCandidate> fused = new LinkedHashMap<>();
        for (List<RetrievalCandidate> rankedList : rankedLists) {
            if (rankedList == null) {
                continue;
            }
            List<RetrievalCandidate> deduped = dedupService.dedup(rankedList);
            for (int i = 0; i < deduped.size(); i++) {
                RetrievalCandidate candidate = deduped.get(i);
                double contribution = 1.0 / (rrfK + i + 1);
                RetrievalCandidate existing = fused.get(candidate.getChunkId());
                if (existing == null) {
                    candidate.setFusedScore(contribution);
                    fused.put(candidate.getChunkId(), candidate);
                } else {
                    existing.setFusedScore(score(existing.getFusedScore()) + contribution);
                    mergeScores(existing, candidate);
                }
            }
        }
        return new ArrayList<>(fused.values()).stream()
                .sorted((a, b) -> Double.compare(score(b.getFusedScore()), score(a.getFusedScore())))
                .limit(Math.max(1, topK))
                .toList();
    }

    private void mergeScores(RetrievalCandidate existing, RetrievalCandidate incoming) {
        if (incoming.getDenseScore() != null) {
            existing.setDenseScore(max(existing.getDenseScore(), incoming.getDenseScore()));
        }
        if (incoming.getSparseScore() != null) {
            existing.setSparseScore(max(existing.getSparseScore(), incoming.getSparseScore()));
        }
        for (String source : incoming.getMatchedBy()) {
            if (!existing.getMatchedBy().contains(source)) {
                existing.getMatchedBy().add(source);
            }
        }
    }

    private double score(Double value) {
        return value == null ? 0.0 : value;
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
