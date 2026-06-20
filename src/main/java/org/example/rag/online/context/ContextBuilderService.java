package org.example.rag.online.context;

import org.example.rag.chunk.TokenEstimator;
import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.RagChunk;
import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.EvidenceContext;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContextBuilderService {

    private final RagOnlineProperties properties;
    private final TokenEstimator tokenEstimator;
    private final CitationService citationService;
    private final ContextCompressorService compressorService;
    private final RagMetadataStoreService metadataStoreService;

    public ContextBuilderService(
            RagOnlineProperties properties,
            TokenEstimator tokenEstimator,
            CitationService citationService,
            ContextCompressorService compressorService,
            RagMetadataStoreService metadataStoreService) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
        this.citationService = citationService;
        this.compressorService = compressorService;
        this.metadataStoreService = metadataStoreService;
    }

    public EvidenceContext build(String query, List<RetrievalCandidate> chunks) {
        EvidenceContext evidenceContext = new EvidenceContext();
        if (chunks == null || chunks.isEmpty()) {
            evidenceContext.setContextText("");
            evidenceContext.setTotalTokens(0);
            return evidenceContext;
        }

        List<RetrievalCandidate> selected = selectAndMerge(chunks);
        StringBuilder context = new StringBuilder();
        int totalTokens = 0;
        int maxTokens = Math.max(500, properties.getContext().getMaxContextTokens());
        List<RetrievalCandidate> used = new ArrayList<>();

        for (int i = 0; i < selected.size(); i++) {
            RetrievalCandidate chunk = selected.get(i);
            String content = contentForContext(chunk);
            if (properties.getContext().isEnableCompression()) {
                content = compressorService.compress(content, Math.max(600, maxTokens * 3));
            }
            int chunkTokens = tokenEstimator.estimate(content);
            if (!used.isEmpty() && totalTokens + chunkTokens > maxTokens) {
                break;
            }
            totalTokens += chunkTokens;
            used.add(chunk);
            context.append("[").append(used.size()).append("] 文件：")
                    .append(defaultText(chunk.getFileName(), "unknown"))
                    .append("\n章节：")
                    .append(defaultText(chunk.getHeadingPath(), "未命名章节"))
                    .append("\n内容：\n")
                    .append(content)
                    .append("\n\n");
        }

        evidenceContext.setContextText(context.toString().trim());
        evidenceContext.setUsedChunks(used);
        evidenceContext.setCitations(citationService.buildCitations(used));
        evidenceContext.setTotalTokens(totalTokens);
        return evidenceContext;
    }

    private List<RetrievalCandidate> selectAndMerge(List<RetrievalCandidate> chunks) {
        Map<String, RetrievalCandidate> bySection = new LinkedHashMap<>();
        for (RetrievalCandidate chunk : chunks) {
            String key = defaultText(chunk.getDocumentId(), "") + "::" + defaultText(chunk.getHeadingPath(), "")
                    + "::" + defaultText(chunk.getChunkId(), "");
            bySection.putIfAbsent(key, chunk);
        }
        return new ArrayList<>(bySection.values());
    }

    private String contentForContext(RetrievalCandidate chunk) {
        String content = defaultText(chunk.getContent(), "");
        if (properties.getContext().isEnableParentExpansion()
                && content.length() < 500
                && chunk.getParentChunkId() != null
                && !chunk.getParentChunkId().isBlank()) {
            return metadataStoreService.findParentChunk(chunk.getParentChunkId())
                    .map(RagChunk::getContent)
                    .filter(parent -> parent != null && !parent.isBlank())
                    .map(parent -> parent.length() > 2400 ? parent.substring(0, 2400) + "..." : parent)
                    .orElse(content);
        }
        return content;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
