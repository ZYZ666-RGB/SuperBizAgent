package org.example.rag.chunk;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TokenEstimator {

    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z0-9_]+");

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = 0;
        StringBuilder nonCjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCjk(ch)) {
                cjk++;
                nonCjk.append(' ');
            } else {
                nonCjk.append(ch);
            }
        }
        int englishWords = 0;
        Matcher matcher = ENGLISH_WORD.matcher(nonCjk);
        while (matcher.find()) {
            englishWords++;
        }
        int symbols = Math.max(0, nonCjk.toString().replaceAll("[A-Za-z0-9_\\s]", "").length() / 4);
        return Math.max(1, cjk + englishWords + symbols);
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
