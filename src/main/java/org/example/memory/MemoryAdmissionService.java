package org.example.memory;

import org.springframework.stereotype.Service;

@Service
public class MemoryAdmissionService {

    private final MemoryProperties memoryProperties;
    private final MemorySafetyService memorySafetyService;

    public MemoryAdmissionService(MemoryProperties memoryProperties, MemorySafetyService memorySafetyService) {
        this.memoryProperties = memoryProperties;
        this.memorySafetyService = memorySafetyService;
    }

    public boolean shouldSave(MemoryCandidate candidate) {
        if (candidate == null || !Boolean.TRUE.equals(candidate.getShouldSave())) {
            return false;
        }
        if (candidate.getContent() == null || candidate.getContent().isBlank()) {
            return false;
        }
        if (!memorySafetyService.isSafe(candidate)) {
            return false;
        }

        if (Boolean.TRUE.equals(candidate.getExplicitSave())
                || "user_explicit".equals(candidate.getSource())) {
            return true;
        }

        MemoryProperties.LongTerm longTerm = memoryProperties.getLongTerm();
        return score(candidate.getEvidenceScore()) >= longTerm.getMinEvidenceScore()
                && score(candidate.getStabilityScore()) >= longTerm.getMinStabilityScore()
                && score(candidate.getFutureUsefulnessScore()) >= longTerm.getMinFutureUsefulnessScore()
                && score(candidate.getConfidence()) >= longTerm.getMinConfidence()
                && score(candidate.getImportance()) >= longTerm.getMinImportance();
    }

    private double score(Double value) {
        return value == null ? 0.0 : value;
    }
}
