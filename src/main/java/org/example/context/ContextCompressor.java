package org.example.context;

import org.example.rag.chunk.TokenEstimator;
import org.springframework.stereotype.Component;

@Component
public class ContextCompressor {

    private final TokenEstimator tokenEstimator;

    public ContextCompressor(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public int estimate(String text) {
        return tokenEstimator.estimate(text);
    }

    public String compressPacket(ContextPacket packet, int maxTokens) {
        if (packet == null) {
            return "";
        }
        String content = hasText(packet.getContent()) ? packet.getContent() : packet.getSummary();
        if (!hasText(content)) {
            return "";
        }
        if (estimate(content) <= maxTokens) {
            return content;
        }
        if (hasText(packet.getSummary()) && estimate(packet.getSummary()) <= maxTokens) {
            return packet.getSummary();
        }
        return fitText(content, maxTokens);
    }

    public String fitText(String text, int maxTokens) {
        if (!hasText(text) || estimate(text) <= maxTokens) {
            return text == null ? "" : text;
        }
        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) / 2;
            String candidate = text.substring(0, mid);
            if (estimate(candidate) <= Math.max(1, maxTokens - 8)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, Math.max(0, best)).stripTrailing()
                + "\n...[context truncated]";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
