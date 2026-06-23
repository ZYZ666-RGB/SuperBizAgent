package org.example.context;

import org.example.memory.LongTermMemoryService;
import org.example.memory.UserMemory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MemoryAdapter {

    private final LongTermMemoryService longTermMemoryService;
    private final ContextConfig config;
    private final ContextCompressor compressor;

    public MemoryAdapter(
            LongTermMemoryService longTermMemoryService,
            ContextConfig config,
            ContextCompressor compressor) {
        this.longTermMemoryService = longTermMemoryService;
        this.config = config;
        this.compressor = compressor;
    }

    public List<ContextPacket> fetchRelevantMemories(RuntimeContext runtimeContext) {
        if (runtimeContext == null || isBlank(runtimeContext.getUserId()) || isBlank(runtimeContext.getQuery())) {
            return List.of();
        }
        int limit = Math.max(3, config.getMemoryTopK());
        List<UserMemory> memories = longTermMemoryService.searchMemories(
                runtimeContext.getUserId(), runtimeContext.getQuery(), null, limit);
        List<ContextPacket> packets = new ArrayList<>();
        for (UserMemory memory : memories) {
            ContextPacket packet = new ContextPacket();
            packet.setType(ContextSourceType.MEMORY);
            packet.setSourceId(memory.getMemoryId());
            packet.setTitle(defaultText(memory.getMemoryType(), "memory"));
            packet.setContent(memory.getContent());
            packet.setSummary(memory.getEvidence());
            packet.setMetadata(new java.util.LinkedHashMap<>());
            packet.getMetadata().put("memoryId", memory.getMemoryId());
            packet.getMetadata().put("memoryType", memory.getMemoryType());
            packet.getMetadata().put("scopeType", memory.getScopeType());
            packet.getMetadata().put("source", memory.getSource());
            packet.setRelevanceScore(lexicalRelevance(runtimeContext.getQuery(), memory.getContent()));
            packet.setRecencyScore(recencyScore(memory.getUpdatedAt() == null ? memory.getCreatedAt() : memory.getUpdatedAt()));
            packet.setImportanceScore(clamp(memory.getImportance() == null ? 0.5 : memory.getImportance()));
            packet.setCreatedAt(memory.getCreatedAt());
            packet.setTokenEstimate(compressor.estimate(defaultText(memory.getContent(), "")));
            packets.add(packet);
        }
        return packets;
    }

    double lexicalRelevance(String query, String content) {
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty() || isBlank(content)) {
            return 0.0;
        }
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String token : queryTokens) {
            if (normalizedContent.contains(token)) {
                hits++;
            }
        }
        return clamp((double) hits / queryTokens.size());
    }

    double recencyScore(LocalDateTime timestamp) {
        if (timestamp == null) {
            return 0.5;
        }
        long days = Math.max(0, Duration.between(timestamp, LocalDateTime.now()).toDays());
        return clamp(1.0 / (1.0 + days / 30.0));
    }

    private Set<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[\\s,，。；;：:()（）\\[\\]{}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
                addCjkBigrams(tokens, token);
            }
        }
        return tokens;
    }

    private void addCjkBigrams(Set<String> tokens, String token) {
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (isCjk(ch)) {
                cjk.append(ch);
            } else {
                addBigrams(tokens, cjk.toString());
                cjk.setLength(0);
            }
        }
        addBigrams(tokens, cjk.toString());
    }

    private void addBigrams(Set<String> tokens, String value) {
        if (value.length() < 2) {
            return;
        }
        for (int i = 0; i < value.length() - 1; i++) {
            tokens.add(value.substring(i, i + 2));
        }
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
