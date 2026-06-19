package org.example.rag.markdown;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class MarkdownCleaner {

    private static final Pattern TOC_DOTS = Pattern.compile("^.{1,120}\\.{5,}\\s*\\d{1,5}\\s*$");
    private static final Pattern PAGE_FOOTER = Pattern.compile("^(page\\s+\\d+|第\\s*\\d+\\s*页|\\d+\\s*/\\s*\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SYMBOL_NOISE = Pattern.compile("^[\\s\\-_=*#·.]{1,12}$");

    public boolean isNoiseLine(String line, Map<String, Integer> lineFrequency) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (TOC_DOTS.matcher(trimmed).matches() || PAGE_FOOTER.matcher(trimmed).matches()) {
            return true;
        }
        if (SYMBOL_NOISE.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.length() <= 80 && lineFrequency.getOrDefault(trimmed, 0) >= 4;
    }

    public boolean isStructuralLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.startsWith("#")
                || trimmed.startsWith("|")
                || trimmed.startsWith("```")
                || trimmed.startsWith("- ")
                || trimmed.startsWith("* ")
                || trimmed.startsWith("+ ")
                || trimmed.startsWith("> ")
                || trimmed.matches("^\\d+\\.\\s+.*");
    }

    public boolean canMergeAsHardWrappedParagraph(String previous, String current) {
        if (previous == null || current == null) {
            return false;
        }
        String left = previous.trim();
        String right = current.trim();
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (isStructuralLine(left) || isStructuralLine(right)) {
            return false;
        }
        if (left.matches(".*[。！？.!?:：；;]$")) {
            return false;
        }
        return Character.isLowerCase(left.charAt(left.length() - 1))
                || Character.isLetterOrDigit(left.charAt(left.length() - 1));
    }
}
