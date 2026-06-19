package org.example.rag.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.rag.chunk.ChunkEnrichService;
import org.example.rag.chunk.MarkdownChunkService;
import org.example.rag.chunk.ParentChildChunkService;
import org.example.rag.config.RagProperties;
import org.example.rag.markdown.MarkdownNormalizeService;
import org.example.rag.model.IndexResult;
import org.example.rag.model.IndexStatus;
import org.example.rag.model.ParsedDocument;
import org.example.rag.model.RagChunk;
import org.example.rag.model.RagDocument;
import org.example.rag.parser.DocumentParserService;
import org.example.rag.parser.FileTypeDetector;
import org.example.rag.util.RagHashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdvancedRagOfflineIndexService {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedRagOfflineIndexService.class);

    private final RagProperties ragProperties;
    private final FileTypeDetector fileTypeDetector;
    private final DocumentParserService documentParserService;
    private final MarkdownNormalizeService markdownNormalizeService;
    private final MarkdownChunkService markdownChunkService;
    private final ParentChildChunkService parentChildChunkService;
    private final ChunkEnrichService chunkEnrichService;
    private final RagEmbeddingService ragEmbeddingService;
    private final MilvusRagIndexService milvusRagIndexService;
    private final RagMetadataStoreService ragMetadataStoreService;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public AdvancedRagOfflineIndexService(
            RagProperties ragProperties,
            FileTypeDetector fileTypeDetector,
            DocumentParserService documentParserService,
            MarkdownNormalizeService markdownNormalizeService,
            MarkdownChunkService markdownChunkService,
            ParentChildChunkService parentChildChunkService,
            ChunkEnrichService chunkEnrichService,
            RagEmbeddingService ragEmbeddingService,
            MilvusRagIndexService milvusRagIndexService,
            RagMetadataStoreService ragMetadataStoreService) {
        this.ragProperties = ragProperties;
        this.fileTypeDetector = fileTypeDetector;
        this.documentParserService = documentParserService;
        this.markdownNormalizeService = markdownNormalizeService;
        this.markdownChunkService = markdownChunkService;
        this.parentChildChunkService = parentChildChunkService;
        this.chunkEnrichService = chunkEnrichService;
        this.ragEmbeddingService = ragEmbeddingService;
        this.milvusRagIndexService = milvusRagIndexService;
        this.ragMetadataStoreService = ragMetadataStoreService;
    }

    public IndexResult indexUploadedFile(MultipartFile file, String namespace) {
        return indexUploadedFile(file, namespace, null);
    }

    public IndexResult indexUploadedFile(MultipartFile file, String namespace, String tags) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        String originalName = safeFileName(file.getOriginalFilename());
        try {
            ensureDirectories();
            Path incoming = uploadDir().resolve(".incoming-" + UUID.randomUUID() + "-" + originalName);
            Files.copy(file.getInputStream(), incoming, StandardCopyOption.REPLACE_EXISTING);
            try {
                return indexFile(incoming, namespace, tags == null || tags.isBlank()
                        ? Map.of()
                        : Map.of("tags", tags));
            } finally {
                Files.deleteIfExists(incoming);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file: " + originalName, e);
        }
    }

    public IndexResult indexFile(Path filePath, String namespace) {
        return indexFile(filePath, namespace, Map.of());
    }

    private IndexResult indexFile(Path filePath, String namespace, Map<String, Object> extraMetadata) {
        String normalizedNamespace = defaultText(namespace, "default");
        IndexResult result = new IndexResult();
        RagDocument document = null;
        try {
            ensureDirectories();
            String fileHash = fileHash(filePath);
            String documentId = RagHashUtils.sha256(normalizedNamespace + ":" + fileHash);
            Path storedFile = storeOriginalFile(filePath, documentId);
            String fileName = filePath.getFileName().toString();
            String fileType = fileTypeDetector.extension(fileName);
            Path markdownPath = markdownDir().resolve(documentId + ".md");
            Path chunkPath = chunkDir().resolve(documentId + "_chunks.json");

            logStep("Start indexing file: " + filePath);
            if (ragProperties.getIndex().isDeleteOldBeforeReindex()) {
                milvusRagIndexService.deleteByDocumentId(documentId);
                ragMetadataStoreService.deleteDocument(documentId);
            }

            document = newDocument(documentId, normalizedNamespace, fileName, fileHash, fileType, storedFile);
            ragMetadataStoreService.upsertDocument(document);
            result.setDocumentId(documentId);
            result.setFileName(fileName);

            updateStatus(documentId, IndexStatus.PARSING);
            ParsedDocument parsedDocument = documentParserService.parse(storedFile, normalizedNamespace);
            parsedDocument.setDocumentId(documentId);
            parsedDocument.setFileHash(fileHash);
            parsedDocument.setFileName(fileName);
            parsedDocument.setFileType(fileType);
            parsedDocument.setSourcePath(storedFile);
            parsedDocument.getMetadata().putAll(extraMetadata);
            saveText(markdownPath, parsedDocument.getMarkdownContent());
            logStep("Markdown parsed. documentId=" + documentId + ", length=" + parsedDocument.getMarkdownContent().length());

            updateStatus(documentId, IndexStatus.NORMALIZING);
            String normalizedMarkdown = markdownNormalizeService.normalize(parsedDocument.getMarkdownContent());
            logStep("Markdown normalized. documentId=" + documentId + ", length=" + normalizedMarkdown.length());

            updateStatus(documentId, IndexStatus.CHUNKING);
            List<RagChunk> childChunks = markdownChunkService.chunk(parsedDocument, normalizedMarkdown);
            List<RagChunk> chunks = parentChildChunkService.attachParents(parsedDocument, childChunks);
            chunks = chunkEnrichService.enrich(chunks, parsedDocument);
            saveChunks(chunkPath, chunks);
            int vectorChunkCount = (int) chunks.stream().filter(chunk -> !Boolean.TRUE.equals(chunk.getParent())).count();
            logStep("Chunking completed. documentId=" + documentId + ", chunks=" + chunks.size());

            updateStatus(documentId, IndexStatus.EMBEDDING);
            Map<String, List<Float>> vectors = ragEmbeddingService.embedChunks(chunks);

            updateStatus(documentId, IndexStatus.INDEXING);
            milvusRagIndexService.upsertChunks(documentId, chunks, vectors);

            ragMetadataStoreService.replaceChunks(documentId, chunks);
            ragMetadataStoreService.updateIndexResult(
                    documentId,
                    markdownPath.toString(),
                    parsedDocument.getParserName(),
                    IndexStatus.COMPLETED,
                    vectorChunkCount);
            logStep("Indexing completed. documentId=" + documentId + ", vectorChunks=" + vectorChunkCount);

            result.setSuccess(true);
            result.setStatus(IndexStatus.COMPLETED);
            result.setChunkCount(vectorChunkCount);
            result.setSourcePath(storedFile.toString());
            result.setMarkdownPath(markdownPath.toString());
            result.setChunkPath(chunkPath.toString());
            result.setMessage("RAG offline indexing completed");
            return result;
        } catch (Exception e) {
            logger.error("[RAG-OFFLINE] Indexing failed", e);
            if (document != null) {
                updateStatusQuietly(document.getDocumentId(), IndexStatus.FAILED);
                result.setDocumentId(document.getDocumentId());
            }
            result.setSuccess(false);
            result.setStatus(IndexStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    public RagDocument getDocument(String documentId) {
        return ragMetadataStoreService.findDocument(documentId)
                .orElseThrow(() -> new IllegalArgumentException("RAG document not found: " + documentId));
    }

    public String getMarkdown(String documentId) {
        RagDocument document = getDocument(documentId);
        try {
            return Files.readString(Path.of(document.getMarkdownPath()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read markdown for document: " + documentId, e);
        }
    }

    public List<RagChunk> getChunks(String documentId) {
        return ragMetadataStoreService.findChunks(documentId);
    }

    public IndexResult reindex(String documentId) {
        RagDocument document = getDocument(documentId);
        return indexFile(Path.of(document.getSourcePath()), document.getNamespace());
    }

    public void deleteDocument(String documentId) {
        RagDocument document = getDocument(documentId);
        milvusRagIndexService.deleteByDocumentId(documentId);
        deleteIfExists(document.getSourcePath());
        deleteIfExists(document.getMarkdownPath());
        deleteIfExists(chunkDir().resolve(documentId + "_chunks.json").toString());
        ragMetadataStoreService.deleteDocument(documentId);
    }

    private RagDocument newDocument(
            String documentId,
            String namespace,
            String fileName,
            String fileHash,
            String fileType,
            Path storedFile) {
        RagDocument document = new RagDocument();
        document.setDocumentId(documentId);
        document.setNamespace(namespace);
        document.setFileName(fileName);
        document.setFileHash(fileHash);
        document.setFileType(fileType);
        document.setSourcePath(storedFile.toString());
        document.setStatus(IndexStatus.UPLOADED);
        document.setChunkCount(0);
        return document;
    }

    private Path storeOriginalFile(Path filePath, String documentId) throws IOException {
        String extension = fileTypeDetector.extension(filePath.getFileName().toString());
        String suffix = extension.isBlank() ? "" : "." + extension;
        Path target = uploadDir().resolve(documentId + suffix);
        if (!filePath.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            Files.copy(filePath, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private String fileHash(Path filePath) throws IOException {
        return RagHashUtils.sha256(java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)));
    }

    private void ensureDirectories() throws IOException {
        Files.createDirectories(uploadDir());
        Files.createDirectories(markdownDir());
        Files.createDirectories(chunkDir());
        Files.createDirectories(logDir());
    }

    private void saveText(Path path, String text) throws IOException {
        Files.writeString(path, text == null ? "" : text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void saveChunks(Path path, List<RagChunk> chunks) throws IOException {
        objectMapper.writeValue(path.toFile(), chunks);
    }

    private void updateStatus(String documentId, IndexStatus status) {
        ragMetadataStoreService.updateStatus(documentId, status);
    }

    private void updateStatusQuietly(String documentId, IndexStatus status) {
        try {
            ragMetadataStoreService.updateStatus(documentId, status);
        } catch (Exception ignored) {
        }
    }

    private void logStep(String message) {
        logger.info("[RAG-OFFLINE] {}", message);
        try {
            Files.writeString(
                    logDir().resolve("index.log"),
                    LocalDateTime.now() + " [RAG-OFFLINE] " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private void deleteIfExists(String path) {
        try {
            if (path != null && !path.isBlank()) {
                Files.deleteIfExists(Path.of(path));
            }
        } catch (Exception e) {
            logger.warn("[RAG-OFFLINE] Failed to delete file {}: {}", path, e.getMessage());
        }
    }

    private Path uploadDir() {
        return Path.of(ragProperties.getStorage().getUploadDir()).normalize();
    }

    private Path markdownDir() {
        return Path.of(ragProperties.getStorage().getMarkdownDir()).normalize();
    }

    private Path chunkDir() {
        return Path.of(ragProperties.getStorage().getChunkDir()).normalize();
    }

    private Path logDir() {
        return Path.of(ragProperties.getStorage().getLogDir()).normalize();
    }

    private String safeFileName(String fileName) {
        String fallback = "upload-" + UUID.randomUUID();
        String value = defaultText(fileName, fallback);
        return value.replace("\\", "_").replace("/", "_").replace(":", "_").trim();
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
