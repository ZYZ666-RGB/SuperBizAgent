package org.example.context;

import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.RagChunk;
import org.example.rag.model.RagDocument;
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
    private final RagMetadataStoreService metadataStoreService;
    private final ContextConfig config;
    private final ContextCompressor compressor;

    public RagEvidenceAdapter(
            AdvancedRagOnlineService advancedRagOnlineService,
            RagMetadataStoreService metadataStoreService,
            ContextConfig config,
            ContextCompressor compressor) {
        this.advancedRagOnlineService = advancedRagOnlineService;
        this.metadataStoreService = metadataStoreService;
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
            if (result != null && result.getCandidates() != null && !result.getCandidates().isEmpty()) {
                return toPackets(result.getCandidates());
            }
            if (shouldUseLatestDocumentFallback(runtimeContext.getQuery())) {
                return latestDocumentPackets(runtimeContext);
            }
            return List.of();
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

    private List<ContextPacket> latestDocumentPackets(RuntimeContext runtimeContext) {
        String namespace = defaultText(runtimeContext.getNamespace(), "default");
        return metadataStoreService.findLatestCompletedDocument(namespace)
                .map(document -> {
                    List<RagChunk> chunks = metadataStoreService.findInitialChildChunks(
                            document.getDocumentId(),
                            Math.max(1, config.getRagTopK()));
                    if (chunks.isEmpty()) {
                        return List.<ContextPacket>of();
                    }
                    logger.info("Using latest uploaded document as RAG fallback. namespace={}, fileName={}, chunks={}",
                            namespace, document.getFileName(), chunks.size());
                    return toPackets(chunks.stream()
                            .map(chunk -> fallbackCandidate(document, chunk))
                            .toList());
                })
                .orElseGet(List::of);
    }

    private RetrievalCandidate fallbackCandidate(RagDocument document, RagChunk chunk) {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId(chunk.getChunkId());
        candidate.setDocumentId(chunk.getDocumentId());
        candidate.setParentChunkId(chunk.getParentChunkId());
        candidate.setNamespace(chunk.getNamespace());
        candidate.setFileName(defaultText(chunk.getFileName(), document.getFileName()));
        candidate.setFileType(defaultText(chunk.getFileType(), document.getFileType()));
        candidate.setSourcePath(defaultText(chunk.getSourcePath(), document.getSourcePath()));
        candidate.setHeadingPath(defaultText(chunk.getHeadingPath(), document.getFileName()));
        candidate.setChunkIndex(chunk.getChunkIndex());
        candidate.setContent(chunk.getContent());
        candidate.setEmbeddingContent(chunk.getEmbeddingContent());
        candidate.setMetadata(chunk.getMetadata());
        candidate.setSparseScore(0.62);
        candidate.getMatchedBy().add("latest-upload-fallback");
        return candidate;
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

    private boolean shouldUseLatestDocumentFallback(String query) {
        String value = defaultText(query, "").toLowerCase(Locale.ROOT);
        return containsAny(value,
                "上传", "文件", "附件", "这个", "这份", "刚才", "刚刚", "上面", "前面",
                "内容", "资料", "材料", "复习", "期末", "考试", "课程", "重点", "知识点");
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

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
