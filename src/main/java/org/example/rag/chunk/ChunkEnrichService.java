package org.example.rag.chunk;

import org.example.rag.config.RagProperties;
import org.example.rag.model.ParsedDocument;
import org.example.rag.model.RagChunk;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkEnrichService {

    private static final Pattern SERVICE_NAME = Pattern.compile("\\b[a-z][a-z0-9-]*-service\\b");
    private static final Pattern ERROR_CODE = Pattern.compile("\\b(ERR_[A-Z0-9_]+|\\d{5})\\b");
    private static final Pattern TRACE_ID = Pattern.compile("(?i)\\btrace[-_ ]?id\\s*[:=]\\s*([A-Za-z0-9\\-]{8,})\\b");
    private static final Pattern LOG_LEVEL = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG|TRACE)\\b");
    private static final List<String> COMPONENTS = List.of("MySQL", "Redis", "Kafka", "Milvus", "MinIO", "Nginx");
    private static final List<String> ALERT_TYPES = List.of("CPU_HIGH", "MEMORY_HIGH", "DISK_FULL", "DB_TIMEOUT");

    private final RagProperties ragProperties;

    public ChunkEnrichService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RagChunk> enrich(List<RagChunk> chunks, ParsedDocument document) {
        for (RagChunk chunk : chunks) {
            chunk.setDocumentId(document.getDocumentId());
            chunk.setNamespace(document.getNamespace());
            chunk.setFileName(document.getFileName());
            chunk.setFileType(document.getFileType());
            chunk.setSourcePath(document.getSourcePath() == null ? "" : document.getSourcePath().toString());
            chunk.setEmbeddingContent(buildEmbeddingContent(chunk));
            chunk.setMetadata(buildMetadata(chunk, document));
        }
        return chunks;
    }

    private String buildEmbeddingContent(RagChunk chunk) {
        if (!ragProperties.getChunk().isEnableContextualEmbedding()) {
            return chunk.getContent();
        }
        return "文档：" + defaultText(chunk.getFileName(), "") + "\n"
                + "章节：" + defaultText(chunk.getHeadingPath(), "") + "\n"
                + "类型：" + defaultText(chunk.getFileType(), "") + "\n\n"
                + defaultText(chunk.getContent(), "");
    }

    private Map<String, Object> buildMetadata(RagChunk chunk, ParsedDocument document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkId", chunk.getChunkId());
        metadata.put("parentChunkId", chunk.getParentChunkId());
        metadata.put("namespace", chunk.getNamespace());
        metadata.put("fileName", chunk.getFileName());
        metadata.put("fileType", chunk.getFileType());
        metadata.put("sourcePath", chunk.getSourcePath());
        metadata.put("headingPath", chunk.getHeadingPath());
        metadata.put("parentHeadingPath", chunk.getParentHeadingPath());
        metadata.put("_source", chunk.getSourcePath());
        metadata.put("_file_name", chunk.getFileName());
        metadata.put("_extension", "." + defaultText(chunk.getFileType(), ""));
        metadata.put("title", chunk.getHeadingPath());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("tokenCount", chunk.getTokenCount());
        metadata.put("parent", Boolean.TRUE.equals(chunk.getParent()));
        metadata.put("parserName", document.getParserName());
        metadata.put("documentMetadata", document.getMetadata());
        if (document.getMetadata().containsKey("tags")) {
            metadata.put("tags", document.getMetadata().get("tags"));
        }
        metadata.put("indexedAt", LocalDateTime.now().toString());
        metadata.putAll(extractAiOpsMetadata(chunk.getContent()));
        return metadata;
    }

    private Map<String, Object> extractAiOpsMetadata(String content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putFirst(metadata, "serviceName", SERVICE_NAME.matcher(defaultText(content, "")));
        putFirst(metadata, "errorCode", ERROR_CODE.matcher(defaultText(content, "")));
        putFirstGroup(metadata, "traceId", TRACE_ID.matcher(defaultText(content, "")), 1);
        putFirst(metadata, "logLevel", LOG_LEVEL.matcher(defaultText(content, "")));

        String lower = defaultText(content, "").toLowerCase(Locale.ROOT);
        COMPONENTS.stream()
                .filter(component -> lower.contains(component.toLowerCase(Locale.ROOT)))
                .findFirst()
                .ifPresent(component -> metadata.put("component", component));
        ALERT_TYPES.stream()
                .filter(content::contains)
                .findFirst()
                .ifPresent(alertType -> metadata.put("alertType", alertType));
        return metadata;
    }

    private void putFirst(Map<String, Object> metadata, String key, Matcher matcher) {
        if (matcher.find()) {
            metadata.put(key, matcher.group());
        }
    }

    private void putFirstGroup(Map<String, Object> metadata, String key, Matcher matcher, int group) {
        if (matcher.find()) {
            metadata.put(key, matcher.group(group));
        }
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
