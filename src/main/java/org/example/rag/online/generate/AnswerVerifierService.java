package org.example.rag.online.generate;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.EvidenceContext;
import org.example.rag.online.model.RetrievalCandidate;
import org.example.rag.online.model.VerificationResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnswerVerifierService {

    private final RagOnlineProperties properties;

    public AnswerVerifierService(RagOnlineProperties properties) {
        this.properties = properties;
    }

    public VerificationResult verify(String query, String answer, EvidenceContext context) {
        VerificationResult result = new VerificationResult();
        if (context == null || context.getUsedChunks().isEmpty()) {
            result.setSupported(false);
            result.setConfidence(0.0);
            result.setReason("No evidence chunks were retrieved.");
            return result;
        }
        double confidence = estimateEvidenceConfidence(context.getUsedChunks());
        if (properties.getVerify().isRequireCitation() && !hasCitation(answer)) {
            confidence -= 0.25;
        }
        if (answer != null && (answer.contains("根据经验") || answer.contains("大概") || answer.contains("可能"))
                && !hasCitation(answer)) {
            confidence -= 0.15;
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        result.setConfidence(confidence);
        result.setSupported(confidence >= properties.getVerify().getMinConfidence());
        result.setReason(result.getSupported() ? "Answer is supported by retrieved evidence." : "Evidence confidence is too low.");
        return result;
    }

    private double estimateEvidenceConfidence(List<RetrievalCandidate> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (RetrievalCandidate chunk : chunks) {
            double score = firstScore(chunk);
            total += Math.min(1.0, score <= 0.0 ? 0.45 : 0.55 + score * 8);
        }
        double average = total / chunks.size();
        if (chunks.size() >= 3) {
            average += 0.08;
        }
        return Math.min(1.0, average);
    }

    private double firstScore(RetrievalCandidate chunk) {
        if (chunk.getRerankScore() != null) {
            return chunk.getRerankScore();
        }
        if (chunk.getFusedScore() != null) {
            return chunk.getFusedScore();
        }
        if (chunk.getSparseScore() != null) {
            return Math.min(0.1, chunk.getSparseScore() / 100.0);
        }
        if (chunk.getDenseScore() != null) {
            return 0.05;
        }
        return 0.0;
    }

    private boolean hasCitation(String answer) {
        return answer != null && answer.matches("(?s).*\\[\\d+].*");
    }
}
