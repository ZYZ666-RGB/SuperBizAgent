package org.example.context;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ToolResultAdapter {

    private final ContextCompressor compressor;

    public ToolResultAdapter(ContextCompressor compressor) {
        this.compressor = compressor;
    }

    public ContextPacket fromRawResult(String toolName, String rawResult, int maxTokens) {
        ContextPacket packet = new ContextPacket();
        packet.setType(ContextSourceType.TOOL_RESULT);
        packet.setSourceId(toolName);
        packet.setTitle(toolName);
        packet.setContent(compressor.fitText(defaultText(rawResult, ""), maxTokens));
        packet.setSummary(packet.getContent());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("toolName", toolName);
        metadata.put("compressed", compressor.estimate(defaultText(rawResult, "")) > maxTokens);
        packet.setMetadata(metadata);
        packet.setRelevanceScore(0.8);
        packet.setRecencyScore(1.0);
        packet.setImportanceScore(0.7);
        packet.setTokenEstimate(compressor.estimate(packet.getContent()));
        return packet;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
