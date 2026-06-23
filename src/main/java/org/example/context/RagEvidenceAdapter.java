package org.example.context;

import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RagQueryResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RagEvidenceAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RagEvidenceAdapter.class);

    private final AdvancedRagOnlineService advancedRagOnlineService;
    private final ContextConfig config;
    private final ContextCompressor compressor;

    public RagEvidenceAdapter(
            AdvancedRagOnlineService advancedRagOnlineService,
            ContextConfig config,
            ContextCompressor compressor) {
        this.advancedRagOnlineService = advancedRagOnlineService;
        this.config = config;
        this.compressor = compressor;
    }

    public List<ContextPacket> fetchEvidence(RuntimeContext runtimeContext) {
        if (runtimeContext == null || isBlank(runtimeContext.getQuery())) {
            return List.of();
        }
        try {
            RagQueryRequest request = new RagQueryRequest();
            request.setQuery(runtimeContext.getQuery());
            request.setNamespace(defaultText(runtimeContext.getNamespace(), "default"));
            request.setTopK(config.getRagTopK());
            request.setEnableHybrid(true);
            request.setEnableRerank(true);
            request.setEnableVerify(false);
            request.setDebug(false);

            RagQueryResult result = advancedRagOnlineService.search(request);
            if (result == null || result.getCandidates() == null) {
                return List.of();
            }
            return toPackets(result.getCandidates());
        } catch (Exception e) {
            logger.warn("RAG evidence retrieval skipped. query={}, error={}",
                    runtimeContext.getQuery(), e.getMessage());
            return List.of();
        }
    }

    private List<ContextPacket> toPackets(List<RetrievalCandidate> candidates) {
        List<ContextPacket> packets = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalCandidate candidate = candidates.get(i);
            double retrievalScore = normalizedRetrievalScore(candidate);
            double score = retrievalScore <= 0.0 ? 0.5 : retrievalScore;
            ContextPacket packet = new ContextPacket();
            packet.setType(ContextSourceType.RAG_EVIDENCE);
            packet.setSourceId(candidate.getChunkId());
            packet.setTitle(defaultText(candidate.getHeadingPath(), candidate.getFileName()));
            packet.setContent(formatEvidence(candidate, score));
            packet.setSummary(candidate.getContent());
            packet.setMetadata(metadata(candidate, score));
            packet.setRelevanceScore(score);
            packet.setRecencyScore(0.6);
            packet.setImportanceScore(0.7);
            packet.setTokenEstimate(compressor.estimate(defaultText(candidate.getContent(), "")));
            packets.add(packet);
        }
        return packets;
    }

    private Map<String, Object> metadata(RetrievalCandidate candidate, double score) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docId", candidate.getDocumentId());
        metadata.put("source", defaultText(candidate.getSourcePath(), candidate.getFileName()));
        metadata.put("title", defaultText(candidate.getHeadingPath(), candidate.getFileName()));
        metadata.put("chunkIndex", candidate.getChunkIndex());
        metadata.put("chunkId", candidate.getChunkId());
        metadata.put("score", score);
        metadata.put("matchedBy", candidate.getMatchedBy());
        return metadata;
    }

    private String formatEvidence(RetrievalCandidate candidate, double score) {
        String source = escapeAttr(defaultText(candidate.getSourcePath(), candidate.getFileName()));
        String title = escapeAttr(defaultText(candidate.getHeadingPath(), candidate.getFileName()));
        String chunk = escapeAttr(candidate.getChunkIndex() == null
                ? defaultText(candidate.getChunkId(), "")
                : candidate.getChunkIndex().toString());
        return """
                <evidence source="%s" title="%s" chunk="%s" score="%.3f">
                %s
                </evidence>
                """.formatted(source, title, chunk, score, defaultText(candidate.getContent(), ""));
    }

    private double normalizedRetrievalScore(RetrievalCandidate candidate) {
        Double score = firstNonNull(
                candidate.getRerankScore(),
                candidate.getFusedScore(),
                candidate.getSparseScore(),
                candidate.getDenseScore());
        if (score == null) {
            return 0.5;
        }
        if (score > 1.0) {
            return 1.0 / (1.0 + score);
        }
        return Math.max(0.0, Math.min(1.0, score));
    }

    private Double firstNonNull(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String escapeAttr(String value) {
        return defaultText(value, "")
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
