package org.example.controller;

import org.example.config.FileUploadConfig;
import org.example.dto.FileUploadRes;
import org.example.memory.EpisodicMemoryService;
import org.example.rag.index.AdvancedRagOfflineIndexService;
import org.example.rag.model.IndexResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    private final FileUploadConfig fileUploadConfig;
    private final AdvancedRagOfflineIndexService ragOfflineIndexService;
    private final EpisodicMemoryService episodicMemoryService;

    public FileUploadController(
            FileUploadConfig fileUploadConfig,
            AdvancedRagOfflineIndexService ragOfflineIndexService,
            EpisodicMemoryService episodicMemoryService) {
        this.fileUploadConfig = fileUploadConfig;
        this.ragOfflineIndexService = ragOfflineIndexService;
        this.episodicMemoryService = episodicMemoryService;
    }

    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "namespace", required = false, defaultValue = "default") String namespace) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResponseEntity.badRequest().body("文件名不能为空");
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedExtension(fileExtension)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions());
        }

        try {
            logger.info("[RAG-OFFLINE] Upload received: {}, namespace={}", originalFilename, namespace);
            IndexResult indexResult = ragOfflineIndexService.indexUploadedFile(file, namespace);
            String indexStatus = indexResult.isSuccess() ? "SUCCESS" : "FAILED";
            String indexError = indexResult.getErrorMessage();
            Path storedPath = indexResult.getSourcePath() == null
                    ? Paths.get(fileUploadConfig.getPath()).resolve(originalFilename).normalize()
                    : Paths.get(indexResult.getSourcePath()).normalize();

            saveUploadEvent(
                    resolveUserId(headerUserId),
                    sessionId,
                    originalFilename,
                    storedPath,
                    file.getSize(),
                    indexStatus,
                    indexError);

            if (!indexResult.isSuccess()) {
                ApiResponse<IndexResult> errorResponse = new ApiResponse<>();
                errorResponse.setCode(500);
                errorResponse.setMessage("RAG 离线入库失败: " + indexResult.getErrorMessage());
                errorResponse.setData(indexResult);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }

            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    storedPath.toString(),
                    file.getSize()
            );
            ApiResponse<FileUploadRes> apiResponse = new ApiResponse<>();
            apiResponse.setCode(200);
            apiResponse.setMessage("success");
            apiResponse.setData(response);
            return ResponseEntity.ok(apiResponse);
        } catch (Exception e) {
            logger.error("[RAG-OFFLINE] Upload indexing failed: {}", originalFilename, e);
            ApiResponse<String> errorResponse = new ApiResponse<>();
            errorResponse.setCode(500);
            errorResponse.setMessage("文件上传或入库失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    private String getFileExtension(String filename) {
        int lastIndexOf = filename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return filename.substring(lastIndexOf + 1).toLowerCase();
    }

    private boolean isAllowedExtension(String extension) {
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return false;
        }
        List<String> allowedList = Arrays.asList(allowedExtensions.split(","));
        return allowedList.contains(extension.toLowerCase());
    }

    private void saveUploadEvent(
            String userId,
            String sessionId,
            String originalFilename,
            Path filePath,
            long fileSize,
            String indexStatus,
            String indexError) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fileName", originalFilename);
        metadata.put("filePath", filePath.toString());
        metadata.put("fileSize", fileSize);
        metadata.put("indexStatus", indexStatus);
        if (indexError != null && !indexError.isBlank()) {
            metadata.put("indexError", indexError);
        }
        episodicMemoryService.saveEvent(
                userId,
                sessionId,
                null,
                "upload_agent",
                "file_upload",
                "User uploaded file " + originalFilename + ". RAG offline indexing status: " + indexStatus + ".",
                metadata,
                0.75);
    }

    private String resolveUserId(String headerUserId) {
        if (headerUserId == null || headerUserId.isBlank()) {
            return "default_user";
        }
        return headerUserId.trim();
    }
}
