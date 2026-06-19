package org.example.rag.controller;

import org.example.rag.index.AdvancedRagOfflineIndexService;
import org.example.rag.model.IndexResult;
import org.example.rag.model.RagChunk;
import org.example.rag.model.RagDocument;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
public class RagDocumentController {

    private final AdvancedRagOfflineIndexService offlineIndexService;

    public RagDocumentController(AdvancedRagOfflineIndexService offlineIndexService) {
        this.offlineIndexService = offlineIndexService;
    }

    @PostMapping(value = "/api/rag/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IndexResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "namespace", required = false, defaultValue = "default") String namespace,
            @RequestParam(value = "tags", required = false) String tags) {
        IndexResult result = offlineIndexService.indexUploadedFile(file, namespace, tags);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.internalServerError().body(result);
    }

    @GetMapping("/api/rag/documents/{documentId}")
    public ResponseEntity<RagDocument> getDocument(@PathVariable String documentId) {
        return ResponseEntity.ok(offlineIndexService.getDocument(documentId));
    }

    @GetMapping(value = "/api/rag/documents/{documentId}/markdown", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getMarkdown(@PathVariable String documentId) {
        return ResponseEntity.ok(offlineIndexService.getMarkdown(documentId));
    }

    @GetMapping("/api/rag/documents/{documentId}/chunks")
    public ResponseEntity<List<RagChunk>> getChunks(@PathVariable String documentId) {
        return ResponseEntity.ok(offlineIndexService.getChunks(documentId));
    }

    @PostMapping("/api/rag/documents/{documentId}/reindex")
    public ResponseEntity<IndexResult> reindex(@PathVariable String documentId) {
        IndexResult result = offlineIndexService.reindex(documentId);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.internalServerError().body(result);
    }

    @DeleteMapping("/api/rag/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String documentId) {
        offlineIndexService.deleteDocument(documentId);
        return ResponseEntity.ok(Map.of("success", true, "documentId", documentId));
    }
}
