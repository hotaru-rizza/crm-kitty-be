package com.inkflow.crm.module.storage.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.storage.dto.PresignedUploadRequest;
import com.inkflow.crm.module.storage.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private static final Set<String> ALLOWED_FOLDERS = Set.of(
            "avatars", "gallery", "sketches", "waivers", "documents", "portfolio", "locations"
    );

    private static final long MAX_DIRECT_UPLOAD_SIZE = 5 * 1024 * 1024; // 5MB

    private final FileStorageService fileStorageService;

    /**
     * Step 1: get a presigned PUT URL → browser uploads directly to R2
     * Used for large files: photos, sketches, waivers
     */
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<FileStorageService.PresignedUploadResult>> getPresignedUrl(
            @Valid @RequestBody PresignedUploadRequest request) {

        validateFolder(request.getFolder());
        validateContentType(request.getContentType());

        FileStorageService.PresignedUploadResult result = fileStorageService.generatePresignedUploadUrl(
                request.getFolder(),
                request.getFilename(),
                request.getContentType()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Direct upload through backend — for small files like avatars (≤5MB)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder) throws IOException {

        validateFolder(folder);
        validateContentType(file.getContentType());

        if (file.getSize() > MAX_DIRECT_UPLOAD_SIZE) {
            throw new IllegalArgumentException("File too large. Use /files/presign for files over 5MB.");
        }

        String url = fileStorageService.uploadFile(
                folder,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );

        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    /**
     * Delete a file by its storage key
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestParam String key) {
        fileStorageService.deleteFile(key);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    private void validateFolder(String folder) {
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Invalid folder: " + folder +
                    ". Allowed: " + ALLOWED_FOLDERS);
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null) return;
        boolean valid = contentType.startsWith("image/")
                || contentType.equals("application/pdf")
                || contentType.equals("image/svg+xml");
        if (!valid) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }
}
