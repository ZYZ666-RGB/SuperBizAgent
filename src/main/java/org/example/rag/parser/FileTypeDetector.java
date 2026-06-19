package org.example.rag.parser;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class FileTypeDetector {

    private static final Set<String> MARKDOWN_FILE_TYPES = Set.of(
            "md", "txt", "log", "json", "yaml", "yml", "java", "py", "sql");
    private static final Set<String> RICH_FILE_TYPES = Set.of(
            "pdf", "docx", "xlsx", "pptx", "html", "htm");

    public String extension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public String contentType(Path path) {
        try {
            String type = Files.probeContentType(path);
            return type == null ? "" : type;
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isMarkdownLike(String fileName) {
        return MARKDOWN_FILE_TYPES.contains(extension(fileName));
    }

    public boolean isRichDocument(String fileName) {
        return RICH_FILE_TYPES.contains(extension(fileName));
    }

    public boolean isCodeFile(String fileName) {
        return Set.of("java", "py", "sql", "json", "yaml", "yml").contains(extension(fileName));
    }

    public String codeFenceLanguage(String fileName) {
        String extension = extension(fileName);
        return switch (extension) {
            case "yml" -> "yaml";
            default -> extension;
        };
    }
}
