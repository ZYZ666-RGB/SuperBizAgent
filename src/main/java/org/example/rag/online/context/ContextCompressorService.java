package org.example.rag.online.context;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ContextCompressorService {

    public String compress(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String[] lines = content.replace("\r\n", "\n").split("\n");
        Set<String> kept = new LinkedHashSet<>();
        boolean inCode = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCode = !inCode;
                kept.add(line);
                continue;
            }
            if (!inCode && trimmed.isBlank()) {
                continue;
            }
            if (!inCode && trimmed.length() <= 1) {
                continue;
            }
            kept.add(line);
        }
        String result = String.join("\n", kept).trim();
        if (result.length() <= maxChars) {
            return result;
        }
        return result.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
