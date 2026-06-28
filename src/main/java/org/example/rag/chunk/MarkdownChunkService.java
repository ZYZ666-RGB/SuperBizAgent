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
            int partStart = block.getStartOffset() == null ? 0 : block.getStartOffset();
            for (String part : splitOversizedContent(block.getContent())) {
                int partTokens = tokenEstimator.estimate(part);
                boolean headingChanged = !currentHeading.equals(headingPath) && current.length() > 0;
                boolean wouldExceedTarget = currentTokens > 0
                        && currentTokens + partTokens > ragProperties.getChunk().getTargetTokens();

                if (headingChanged || wouldExceedTarget) {
                    chunks.add(toChunk(document, current.toString(), currentHeading, currentStart, currentEnd, chunkIndex++));
                    int overlapBudget = Math.max(0, ragProperties.getChunk().getTargetTokens() - partTokens);
                    String overlap = headingChanged ? "" : overlap(current.toString(), overlapBudget);
                    current.setLength(0);
                    if (!overlap.isBlank()) {
                        current.append(overlap).append("\n\n");
                    }
                    currentTokens = tokenEstimator.estimate(overlap);
                }

                if (current.length() == 0) {
                    currentStart = partStart;
                    currentHeading = headingPath;
                }
                current.append(part.trim()).append("\n\n");
                currentEnd = Math.min(
                        block.getEndOffset() == null ? partStart + part.length() : block.getEndOffset(),
                        partStart + part.length());
                currentTokens += partTokens;
                partStart += part.length();
            }
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

    private String overlap(String content, int maxOverlapTokens) {
        int overlapTokens = Math.min(
                Math.max(0, ragProperties.getChunk().getOverlapTokens()),
                Math.max(0, maxOverlapTokens));
        if (overlapTokens == 0 || content.isBlank()) {
            return "";
        }
        String normalized = content.trim();
        StringBuilder builder = new StringBuilder();
        int tokens = 0;
        for (int offset = normalized.length(); offset > 0; ) {
            int codePoint = normalized.codePointBefore(offset);
            String text = new String(Character.toChars(codePoint));
            int nextTokens = Math.max(1, tokenEstimator.estimate(text));
            if (tokens + nextTokens > overlapTokens) {
                break;
            }
            builder.insert(0, text);
            tokens += nextTokens;
            offset -= Character.charCount(codePoint);
        }
        return builder.toString().trim();
    }

    private List<String> splitOversizedContent(String content) {
        String normalized = defaultText(content, "").trim();
        int maxTokens = Math.max(100, ragProperties.getChunk().getMaxTokens());
        if (tokenEstimator.estimate(normalized) <= maxTokens) {
            return List.of(normalized);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            String text = new String(Character.toChars(codePoint));
            int tokens = Math.max(1, tokenEstimator.estimate(text));
            if (!current.isEmpty() && currentTokens + tokens > maxTokens) {
                parts.add(current.toString().trim());
                current.setLength(0);
                currentTokens = 0;
            }
            current.appendCodePoint(codePoint);
            currentTokens += tokens;
            offset += Character.charCount(codePoint);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts.stream()
                .filter(part -> !part.isBlank())
                .toList();
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
