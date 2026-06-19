package org.example.rag.markdown;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MarkdownNormalizeService {

    private final MarkdownCleaner markdownCleaner;

    public MarkdownNormalizeService(MarkdownCleaner markdownCleaner) {
        this.markdownCleaner = markdownCleaner;
    }

    public String normalize(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String unified = markdown.replace("\r\n", "\n").replace('\r', '\n');
        Map<String, Integer> frequency = Arrays.stream(unified.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toMap(line -> line, line -> 1, Integer::sum));

        StringBuilder normalized = new StringBuilder();
        boolean inCodeBlock = false;
        int blankLines = 0;

        for (String rawLine : unified.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                appendLine(normalized, line);
                blankLines = 0;
                continue;
            }
            if (!inCodeBlock && markdownCleaner.isNoiseLine(line, frequency)) {
                continue;
            }
            if (trimmed.isEmpty()) {
                blankLines++;
                if (blankLines <= 1) {
                    appendLine(normalized, "");
                }
                continue;
            }

            blankLines = 0;
            if (!inCodeBlock && tryMergeHardWrap(normalized, line)) {
                continue;
            }
            appendLine(normalized, line);
        }
        return normalized.toString().trim() + "\n";
    }

    private boolean tryMergeHardWrap(StringBuilder normalized, String line) {
        int lastBreak = normalized.lastIndexOf("\n");
        if (lastBreak <= 0) {
            return false;
        }
        int previousBreak = normalized.lastIndexOf("\n", lastBreak - 1);
        int previousStart = previousBreak < 0 ? 0 : previousBreak + 1;
        String previous = normalized.substring(previousStart, lastBreak);
        if (!markdownCleaner.canMergeAsHardWrappedParagraph(previous, line)) {
            return false;
        }
        normalized.delete(lastBreak, normalized.length());
        normalized.append(' ').append(line).append('\n');
        return true;
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }
}
