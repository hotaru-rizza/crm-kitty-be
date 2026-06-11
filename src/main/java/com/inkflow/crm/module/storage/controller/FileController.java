package com.inkflow.crm.module.storage.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.storage.dto.PresignedUploadRequest;
import com.inkflow.crm.module.storage.dto.PresignedUploadResult;
import com.inkflow.crm.module.storage.service.FileStorageService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "CRM · Files")
public class FileController {

    private static final long MAX_DIRECT_UPLOAD_BYTES = 5L * 1024 * 1024;

    private final FileStorageService fileStorageService;

    @PostMapping("/presign")
    @RequirePermission(Permission.FILES_UPLOAD)
    public ResponseEntity<ApiResponse<PresignedUploadResult>> getPresignedUrl(
            @Valid @RequestBody PresignedUploadRequest request) {
        validateFolder(request.getFolder());
        validateContentType(request.getContentType());

        PresignedUploadResult result = fileStorageService.generatePresignedUploadUrl(
                request.getFolder(),
                request.getFilename(),
                request.getContentType()
        );
        log.info("Presigned upload URL generated via API: folder={} filename={}", request.getFolder(), request.getFilename());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permission.FILES_UPLOAD)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder) throws IOException {
        validateFolder(folder);
        validateContentType(file.getContentType());

        if (file.getSize() > MAX_DIRECT_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File too large. Use /files/presign for files over 5MB.");
        }

        String url = fileStorageService.uploadFile(
                folder,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );
        log.info("File uploaded via API: folder={} size={}", folder, file.getSize());

        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    @DeleteMapping
    @RequirePermission(Permission.FILES_UPLOAD)
    public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestParam String key) {
        fileStorageService.deleteFile(key);
        log.info("File deleted via API: key={}", key);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    private void validateFolder(String folder) {
        if (!FileStorageService.ALLOWED_FOLDERS.contains(folder)) {
            throw new IllegalArgumentException(
                    "Invalid folder: " + folder + ". Allowed: " + FileStorageService.ALLOWED_FOLDERS
            );
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null) {
            return;
        }

        boolean valid = contentType.startsWith("image/")
                || contentType.equals("application/pdf")
                || contentType.equals("image/svg+xml");

        if (!valid) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }
}
