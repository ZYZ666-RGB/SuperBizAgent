package org.example.memory;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class MemorySafetyService {

    private final List<Pattern> sensitivePatterns = List.of(
            Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|access[_-]?key|token)\\s*[:=]\\s*\\S+"),
            Pattern.compile("(?i)(sk-[A-Za-z0-9_-]{16,}|ghp_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{20,})"),
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)")
    );

    public boolean isSafe(MemoryCandidate candidate) {
        if (candidate == null || candidate.getSafetyScore() == null || candidate.getSafetyScore() != 1) {
            return false;
        }
        return isTextSafe(candidate.getContent()) && isTextSafe(candidate.getEvidence());
    }

    public boolean isTextSafe(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        for (Pattern pattern : sensitivePatterns) {
            if (pattern.matcher(text).find()) {
                return false;
            }
        }
        return true;
    }
}
