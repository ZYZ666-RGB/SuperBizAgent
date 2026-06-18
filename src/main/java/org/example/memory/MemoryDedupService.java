package org.example.memory;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MemoryDedupService {

    private final UserMemoryRepository userMemoryRepository;

    public MemoryDedupService(UserMemoryRepository userMemoryRepository) {
        this.userMemoryRepository = userMemoryRepository;
    }

    public boolean isDuplicate(String userId, MemoryCandidate candidate) {
        if (candidate == null || candidate.getContent() == null) {
            return true;
        }
        String normalizedCandidate = normalize(candidate.getContent());
        return userMemoryRepository.findEnabledByUserAndType(
                        userId, defaultText(candidate.getMemoryType(), "semantic"), 200)
                .stream()
                .map(UserMemory::getContent)
                .map(this::normalize)
                .anyMatch(normalizedCandidate::equals);
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
