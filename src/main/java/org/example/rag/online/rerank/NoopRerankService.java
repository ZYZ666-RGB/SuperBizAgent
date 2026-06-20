package org.example.rag.online.rerank;

import org.example.rag.online.model.RerankResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoopRerankService {

    public RerankResult rerank(String query, List<RetrievalCandidate> candidates, int topK) {
        long start = System.currentTimeMillis();
        List<RetrievalCandidate> ranked = candidates == null ? List.of() : candidates.stream()
                .peek(candidate -> candidate.setRerankScore(primaryScore(candidate)))
                .sorted((a, b) -> Double.compare(score(b.getRerankScore()), score(a.getRerankScore())))
                .limit(Math.max(1, topK))
                .toList();
        RerankResult result = new RerankResult();
        result.setCandidates(ranked);
        result.setSuccess(true);
        result.setProvider("noop");
        result.setMessage("Rerank disabled or unavailable; used fused retrieval score.");
        result.setTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private double primaryScore(RetrievalCandidate candidate) {
        if (candidate.getFusedScore() != null) {
            return candidate.getFusedScore();
        }
        if (candidate.getSparseScore() != null) {
            return candidate.getSparseScore();
        }
        if (candidate.getDenseScore() != null) {
            return candidate.getDenseScore();
        }
        return 0.0;
    }

    private double score(Double value) {
        return value == null ? 0.0 : value;
    }
}
