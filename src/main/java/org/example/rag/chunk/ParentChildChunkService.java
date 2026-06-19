package org.example.rag.chunk;

import org.example.rag.config.RagProperties;
import org.example.rag.model.ParsedDocument;
import org.example.rag.model.RagChunk;
import org.example.rag.util.RagHashUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParentChildChunkService {

    private final RagProperties ragProperties;
    private final TokenEstimator tokenEstimator;

    public ParentChildChunkService(RagProperties ragProperties, TokenEstimator tokenEstimator) {
        this.ragProperties = ragProperties;
        this.tokenEstimator = tokenEstimator;
    }

    public List<RagChunk> attachParents(ParsedDocument document, List<RagChunk> childChunks) {
        if (!ragProperties.getChunk().isEnableParentChild() || childChunks.isEmpty()) {
            return childChunks;
        }
        Map<String, List<RagChunk>> byParentHeading = new LinkedHashMap<>();
        for (RagChunk child : childChunks) {
            String parentHeading = parentHeadingPath(child.getHeadingPath(), document.getFileName());
            byParentHeading.computeIfAbsent(parentHeading, key -> new ArrayList<>()).add(child);
        }

        List<RagChunk> result = new ArrayList<>();
        int parentIndex = -1;
        for (Map.Entry<String, List<RagChunk>> entry : byParentHeading.entrySet()) {
            RagChunk parent = parentChunk(document, entry.getKey(), entry.getValue(), parentIndex--);
            result.add(parent);
            for (RagChunk child : entry.getValue()) {
                child.setParentChunkId(parent.getChunkId());
                child.setParentHeadingPath(parent.getHeadingPath());
                result.add(child);
            }
        }
        return result;
    }

    private RagChunk parentChunk(ParsedDocument document, String headingPath, List<RagChunk> children, int parentIndex) {
        StringBuilder content = new StringBuilder();
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (RagChunk child : children) {
            content.append(child.getContent()).append("\n\n");
            start = Math.min(start, child.getStartOffset() == null ? 0 : child.getStartOffset());
            end = Math.max(end, child.getEndOffset() == null ? 0 : child.getEndOffset());
        }
        RagChunk parent = new RagChunk();
        parent.setDocumentId(document.getDocumentId());
        parent.setNamespace(document.getNamespace());
        parent.setFileName(document.getFileName());
        parent.setFileType(document.getFileType());
        parent.setSourcePath(document.getSourcePath() == null ? "" : document.getSourcePath().toString());
        parent.setHeadingPath(headingPath);
        parent.setParentHeadingPath(headingPath);
        parent.setChunkIndex(parentIndex);
        parent.setStartOffset(start == Integer.MAX_VALUE ? 0 : start);
        parent.setEndOffset(end);
        parent.setContent(content.toString().trim());
        parent.setTokenCount(tokenEstimator.estimate(parent.getContent()));
        parent.setParent(true);
        parent.setChunkId(RagHashUtils.sha256(document.getDocumentId()
                + "|parent|" + headingPath + "|" + RagHashUtils.sha256(parent.getContent())));
        return parent;
    }

    private String parentHeadingPath(String headingPath, String fallback) {
        if (headingPath == null || headingPath.isBlank()) {
            return fallback == null ? "Document" : fallback;
        }
        String[] parts = headingPath.split("\\s*>\\s*");
        if (parts.length <= 2) {
            return headingPath;
        }
        return parts[0] + " > " + parts[1];
    }
}
