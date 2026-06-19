package org.example.rag.markdown;

import org.example.rag.chunk.TokenEstimator;
import org.example.rag.model.MarkdownBlock;
import org.example.rag.util.RagHashUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownSectionParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern LIST = Pattern.compile("^\\s*([-*+]\\s+|\\d+\\.\\s+).+");

    private final TokenEstimator tokenEstimator;

    public MarkdownSectionParser(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<MarkdownBlock> parse(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return blocks;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("(?<=\n)", -1);
        List<String> headingStack = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String blockType = null;
        int blockStart = 0;
        int offset = 0;
        boolean inCode = false;
        String codeHeadingPath = "";

        for (String rawLine : lines) {
            String line = rawLine.endsWith("\n") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            String trimmed = line.trim();
            Matcher headingMatcher = HEADING.matcher(trimmed);

            if (!inCode && headingMatcher.matches()) {
                flush(blocks, buffer, blockType, headingPath(headingStack), blockStart, offset);
                buffer.setLength(0);
                blockType = null;
                int level = headingMatcher.group(1).length();
                updateHeadingStack(headingStack, level, headingMatcher.group(2));
                blocks.add(block("heading", line, headingPath(headingStack), level, offset, offset + rawLine.length()));
                offset += rawLine.length();
                continue;
            }

            if (trimmed.startsWith("```")) {
                if (!inCode) {
                    flush(blocks, buffer, blockType, headingPath(headingStack), blockStart, offset);
                    buffer.setLength(0);
                    blockType = "code";
                    blockStart = offset;
                    codeHeadingPath = headingPath(headingStack);
                    inCode = true;
                }
                buffer.append(rawLine);
                offset += rawLine.length();
                if (inCode && buffer.toString().split("```", -1).length > 2) {
                    flush(blocks, buffer, "code", codeHeadingPath, blockStart, offset);
                    buffer.setLength(0);
                    blockType = null;
                    inCode = false;
                }
                continue;
            }

            if (inCode) {
                buffer.append(rawLine);
                offset += rawLine.length();
                continue;
            }

            if (trimmed.isEmpty()) {
                flush(blocks, buffer, blockType, headingPath(headingStack), blockStart, offset);
                buffer.setLength(0);
                blockType = null;
                offset += rawLine.length();
                continue;
            }

            String nextType = classify(trimmed);
            if (blockType != null && !blockType.equals(nextType)) {
                flush(blocks, buffer, blockType, headingPath(headingStack), blockStart, offset);
                buffer.setLength(0);
                blockType = null;
            }
            if (blockType == null) {
                blockType = nextType;
                blockStart = offset;
            }
            buffer.append(rawLine);
            offset += rawLine.length();
        }

        flush(blocks, buffer, blockType, headingPath(headingStack), blockStart, offset);
        return blocks;
    }

    private String classify(String trimmed) {
        if (trimmed.startsWith("|")) {
            return "table";
        }
        if (trimmed.startsWith(">")) {
            return "quote";
        }
        if (LIST.matcher(trimmed).matches()) {
            return "list";
        }
        if (trimmed.matches("^(Caused by:|at\\s+\\S+\\(.+\\)|ERROR\\b|WARN\\b|INFO\\b).*")) {
            return "code";
        }
        return "paragraph";
    }

    private void flush(
            List<MarkdownBlock> blocks,
            StringBuilder buffer,
            String type,
            String headingPath,
            int start,
            int end) {
        if (buffer.isEmpty()) {
            return;
        }
        blocks.add(block(type == null ? "paragraph" : type, buffer.toString().trim(), headingPath, null, start, end));
    }

    private MarkdownBlock block(String type, String content, String headingPath, Integer level, int start, int end) {
        MarkdownBlock block = new MarkdownBlock();
        block.setType(type);
        block.setContent(content);
        block.setHeadingPath(headingPath);
        block.setHeadingLevel(level);
        block.setStartOffset(start);
        block.setEndOffset(end);
        block.setTokenCount(tokenEstimator.estimate(content));
        block.setBlockId(RagHashUtils.sha256(type + "|" + headingPath + "|" + start + "|" + content));
        return block;
    }

    private void updateHeadingStack(List<String> headingStack, int level, String heading) {
        while (headingStack.size() >= level) {
            headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(heading.trim());
    }

    private String headingPath(List<String> headingStack) {
        return String.join(" > ", headingStack);
    }
}
