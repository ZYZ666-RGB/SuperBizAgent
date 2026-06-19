package org.example.rag.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rag.model.IndexStatus;
import org.example.rag.model.RagChunk;
import org.example.rag.model.RagDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RagMetadataStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagMetadataStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<RagDocument> documentRowMapper = (rs, rowNum) -> {
        RagDocument document = new RagDocument();
        document.setId(rs.getLong("id"));
        document.setDocumentId(rs.getString("document_id"));
        document.setNamespace(rs.getString("namespace"));
        document.setFileName(rs.getString("file_name"));
        document.setFileHash(rs.getString("file_hash"));
        document.setFileType(rs.getString("file_type"));
        document.setSourcePath(rs.getString("source_path"));
        document.setMarkdownPath(rs.getString("markdown_path"));
        document.setParserName(rs.getString("parser_name"));
        String status = rs.getString("status");
        document.setStatus(status == null ? null : IndexStatus.valueOf(status));
        document.setChunkCount(rs.getInt("chunk_count"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            document.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            document.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return document;
    };

    private final RowMapper<RagChunk> chunkRowMapper = (rs, rowNum) -> {
        RagChunk chunk = new RagChunk();
        chunk.setChunkId(rs.getString("chunk_id"));
        chunk.setDocumentId(rs.getString("document_id"));
        chunk.setParentChunkId(rs.getString("parent_chunk_id"));
        chunk.setNamespace(rs.getString("namespace"));
        chunk.setFileName(rs.getString("file_name"));
        chunk.setHeadingPath(rs.getString("heading_path"));
        chunk.setChunkIndex(rs.getInt("chunk_index"));
        chunk.setTokenCount(rs.getInt("token_count"));
        chunk.setContent(rs.getString("content"));
        chunk.setEmbeddingContent(rs.getString("embedding_content"));
        String metadata = rs.getString("metadata_json");
        if (metadata != null && !metadata.isBlank()) {
            try {
                chunk.setMetadata(objectMapper.readValue(metadata, new TypeReference<>() {
                }));
            } catch (Exception ignored) {
                chunk.getMetadata().put("rawMetadata", metadata);
            }
        }
        Object parent = chunk.getMetadata().get("parent");
        if (parent instanceof Boolean value) {
            chunk.setParent(value);
        }
        chunk.setFileType((String) chunk.getMetadata().getOrDefault("fileType", null));
        chunk.setSourcePath((String) chunk.getMetadata().getOrDefault("sourcePath", null));
        chunk.setParentHeadingPath((String) chunk.getMetadata().getOrDefault("parentHeadingPath", null));
        return chunk;
    };

    public void upsertDocument(RagDocument document) {
        deleteDocumentOnly(document.getDocumentId());
        String sql = """
                INSERT INTO rag_document(
                    document_id, namespace, file_name, file_hash, file_type, source_path,
                    markdown_path, parser_name, status, chunk_count, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                sql,
                document.getDocumentId(),
                document.getNamespace(),
                document.getFileName(),
                document.getFileHash(),
                document.getFileType(),
                document.getSourcePath(),
                document.getMarkdownPath(),
                document.getParserName(),
                document.getStatus() == null ? null : document.getStatus().name(),
                document.getChunkCount() == null ? 0 : document.getChunkCount(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now));
    }

    public void updateStatus(String documentId, IndexStatus status) {
        jdbcTemplate.update(
                "UPDATE rag_document SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE document_id = ?",
                status.name(),
                documentId);
    }

    public void updateIndexResult(String documentId, String markdownPath, String parserName, IndexStatus status, int chunkCount) {
        jdbcTemplate.update("""
                        UPDATE rag_document
                        SET markdown_path = ?,
                            parser_name = ?,
                            status = ?,
                            chunk_count = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE document_id = ?
                        """,
                markdownPath,
                parserName,
                status.name(),
                chunkCount,
                documentId);
    }

    public void replaceChunks(String documentId, List<RagChunk> chunks) {
        deleteChunks(documentId);
        String sql = """
                INSERT INTO rag_chunk(
                    chunk_id, document_id, parent_chunk_id, namespace, file_name,
                    heading_path, chunk_index, token_count, content, embedding_content,
                    metadata_json, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, chunks.stream()
                .map(chunk -> new Object[]{
                        chunk.getChunkId(),
                        chunk.getDocumentId(),
                        chunk.getParentChunkId(),
                        chunk.getNamespace(),
                        chunk.getFileName(),
                        chunk.getHeadingPath(),
                        chunk.getChunkIndex(),
                        chunk.getTokenCount(),
                        chunk.getContent(),
                        chunk.getEmbeddingContent(),
                        toJson(chunk.getMetadata()),
                        Timestamp.valueOf(LocalDateTime.now())
                })
                .toList());
    }

    public Optional<RagDocument> findDocument(String documentId) {
        List<RagDocument> documents = jdbcTemplate.query(
                "SELECT * FROM rag_document WHERE document_id = ? LIMIT 1",
                documentRowMapper,
                documentId);
        return documents.isEmpty() ? Optional.empty() : Optional.of(documents.get(0));
    }

    public List<RagChunk> findChunks(String documentId) {
        return jdbcTemplate.query(
                "SELECT * FROM rag_chunk WHERE document_id = ? ORDER BY chunk_index ASC, id ASC",
                chunkRowMapper,
                documentId);
    }

    public void deleteDocument(String documentId) {
        deleteChunks(documentId);
        deleteDocumentOnly(documentId);
    }

    public void deleteChunks(String documentId) {
        jdbcTemplate.update("DELETE FROM rag_chunk WHERE document_id = ?", documentId);
    }

    private void deleteDocumentOnly(String documentId) {
        jdbcTemplate.update("DELETE FROM rag_document WHERE document_id = ?", documentId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
