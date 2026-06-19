package org.example.rag.chunk;

import org.example.rag.config.RagProperties;
import org.example.rag.markdown.MarkdownSectionParser;
import org.example.rag.model.MarkdownBlock;
import org.example.rag.model.ParsedDocument;
import org.example.rag.model.RagChunk;
import org.example.rag.util.RagHashUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarkdownChunkService {

    private final MarkdownSectionParser sectionParser;
    private final TokenEstimator tokenEstimator;
    private final RagProperties ragProperties;

    public MarkdownChunkService(
            MarkdownSectionParser sectionParser,
            TokenEstimator tokenEstimator,
            RagProperties ragProperties) {
        this.sectionParser = sectionParser;
        this.tokenEstimator = tokenEstimator;
        this.ragProperties = ragProperties;
    }

    public List<RagChunk> chunk(ParsedDocument document, String normalizedMarkdown) {
        List<MarkdownBlock> blocks = sectionParser.parse(normalizedMarkdown);
        List<RagChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        int currentStart = 0;
        int currentEnd = 0;
        int currentTokens = 0;
        int chunkIndex = 0;

        for (MarkdownBlock block : blocks) {
            if ("heading".equals(block.getType())) {
                continue;
            }
            if (block.getContent() == null || block.getContent().isBlank()) {
                continue;
            }
            String headingPath = defaultText(block.getHeadingPath(), "");
            int blockTokens = block.getTokenCount() == null ? tokenEstimator.estimate(block.getContent()) : block.getTokenCount();
            boolean headingChanged = !currentHeading.equals(headingPath) && current.length() > 0;
            boolean wouldExceedTarget = currentTokens > 0
                    && currentTokens + blockTokens > ragProperties.getChunk().getTargetTokens();

            if (headingChanged || wouldExceedTarget) {
                chunks.add(toChunk(document, current.toString(), currentHeading, currentStart, currentEnd, chunkIndex++));
                String overlap = headingChanged ? "" : overlap(current.toString());
                current.setLength(0);
                if (!overlap.isBlank()) {
                    current.append(overlap).append("\n\n");
                }
                currentTokens = tokenEstimator.estimate(overlap);
            }

            if (current.length() == 0) {
                currentStart = block.getStartOffset() == null ? 0 : block.getStartOffset();
                currentHeading = headingPath;
            }
            current.append(block.getContent().trim()).append("\n\n");
            currentEnd = block.getEndOffset() == null ? currentStart + current.length() : block.getEndOffset();
            currentTokens += blockTokens;
        }

        if (!current.isEmpty()) {
            chunks.add(toChunk(document, current.toString(), currentHeading, currentStart, currentEnd, chunkIndex));
        }
        return mergeShortChunks(document, chunks);
    }

    private RagChunk toChunk(
            ParsedDocument document,
            String content,
            String headingPath,
            int startOffset,
            int endOffset,
            int chunkIndex) {
        String normalizedContent = content.trim();
        RagChunk chunk = new RagChunk();
        chunk.setDocumentId(document.getDocumentId());
        chunk.setNamespace(document.getNamespace());
        chunk.setFileName(document.getFileName());
        chunk.setFileType(document.getFileType());
        chunk.setSourcePath(document.getSourcePath() == null ? "" : document.getSourcePath().toString());
        chunk.setHeadingPath(defaultText(headingPath, document.getFileName()));
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartOffset(startOffset);
        chunk.setEndOffset(endOffset);
        chunk.setTokenCount(tokenEstimator.estimate(normalizedContent));
        chunk.setContent(normalizedContent);
        chunk.setParent(false);
        chunk.setChunkId(stableChunkId(chunk));
        return chunk;
    }

    private List<RagChunk> mergeShortChunks(ParsedDocument document, List<RagChunk> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        List<RagChunk> merged = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            if (!merged.isEmpty()
                    && chunk.getTokenCount() < ragProperties.getChunk().getMinTokens()
                    && merged.get(merged.size() - 1).getTokenCount() + chunk.getTokenCount()
                    <= ragProperties.getChunk().getMaxTokens()) {
                RagChunk previous = merged.get(merged.size() - 1);
                previous.setContent(previous.getContent() + "\n\n" + chunk.getContent());
                previous.setEndOffset(chunk.getEndOffset());
                previous.setTokenCount(tokenEstimator.estimate(previous.getContent()));
                previous.setChunkId(stableChunkId(previous));
            } else {
                merged.add(chunk);
            }
        }
        for (int i = 0; i < merged.size(); i++) {
            RagChunk chunk = merged.get(i);
            chunk.setChunkIndex(i);
            chunk.setChunkId(stableChunkId(chunk));
        }
        return merged;
    }

    private String overlap(String content) {
        int overlapTokens = Math.max(0, ragProperties.getChunk().getOverlapTokens());
        if (overlapTokens == 0 || content.isBlank()) {
            return "";
        }
        String[] words = content.trim().split("\\s+");
        if (words.length <= overlapTokens) {
            return content.trim();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = Math.max(0, words.length - overlapTokens); i < words.length; i++) {
            builder.append(words[i]).append(' ');
        }
        return builder.toString().trim();
    }

    private String stableChunkId(RagChunk chunk) {
        return RagHashUtils.sha256(chunk.getDocumentId()
                + "|" + chunk.getChunkIndex()
                + "|" + chunk.getHeadingPath()
                + "|" + RagHashUtils.sha256(chunk.getContent()));
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
