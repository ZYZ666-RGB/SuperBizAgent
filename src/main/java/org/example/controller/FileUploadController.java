package org.example.controller;

import org.example.config.FileUploadConfig;
import org.example.dto.FileUploadRes;
import org.example.memory.EpisodicMemoryService;
import org.example.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private FileUploadConfig fileUploadConfig;

    @Autowired
    private VectorIndexService vectorIndexService;

    @Autowired
    private EpisodicMemoryService episodicMemoryService;

    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return ResponseEntity.badRequest().body("文件名不能为空");
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedExtension(fileExtension)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions());
        }

        try {
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 使用原始文件名，而不是UUID，以便实现基于文件名的去重
            Path filePath = uploadDir.resolve(originalFilename).normalize();
            
            // 如果文件已存在，先删除旧文件（实现覆盖更新）
            if (Files.exists(filePath)) {
                logger.info("文件已存在，将覆盖: {}", filePath);
                Files.delete(filePath);
            }
            
            Files.copy(file.getInputStream(), filePath);
            String indexStatus = "SUCCESS";
            String indexError = null;

            logger.info("文件上传成功: {}", filePath);

            // 文件上传成功后，自动调用向量索引服务
            try {
                logger.info("开始为上传文件创建向量索引: {}", filePath);
                vectorIndexService.indexSingleFile(filePath.toString());
                logger.info("向量索引创建成功: {}", filePath);
            } catch (Exception e) {
                indexStatus = "FAILED";
                indexError = e.getMessage();
                logger.error("向量索引创建失败: {}, 错误: {}", filePath, e.getMessage(), e);
                // 注意：即使索引失败，文件上传仍然成功，只是记录错误日志
                // 可以根据业务需求决定是否要删除文件或返回错误
            }

            saveUploadEvent(
                    resolveUserId(headerUserId),
                    sessionId,
                    originalFilename,
                    filePath,
                    file.getSize(),
                    indexStatus,
                    indexError);

            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );

            // 使用统一的API响应格式
            ApiResponse<FileUploadRes> apiResponse = new ApiResponse<>();
            apiResponse.setCode(200);
            apiResponse.setMessage("success");
            apiResponse.setData(response);
            
            return ResponseEntity.ok(apiResponse);

        } catch (IOException e) {
            ApiResponse<String> errorResponse = new ApiResponse<>();
            errorResponse.setCode(500);
            errorResponse.setMessage("文件上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * 统一 API 响应格式
     */
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
                "User uploaded file " + originalFilename + ". Vector indexing status: " + indexStatus + ".",
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
