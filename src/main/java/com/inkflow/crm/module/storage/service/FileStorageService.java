package com.inkflow.crm.module.storage.service;

import com.inkflow.crm.config.R2Properties;
import com.inkflow.crm.module.storage.dto.PresignedUploadResult;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    public static final Set<String> ALLOWED_FOLDERS = Set.of(
            "avatars", "gallery", "sketches", "portfolio", "locations", "studio"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties r2Properties;

    public PresignedUploadResult generatePresignedUploadUrl(String folder, String originalFilename, String contentType) {
        String key = buildTenantKey(folder, originalFilename);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(r2Properties.getBucketName())
                        .key(key)
                        .contentType(contentType)
                        .build())
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUploadResult(
                key,
                presigned.url().toString(),
                presigned.httpRequest().method().name(),
                Map.copyOf(presigned.signedHeaders()),
                buildPublicUrl(key)
        );
    }

    public String uploadFile(String folder, String originalFilename, String contentType, InputStream inputStream, long contentLength) {
        String key = buildTenantKey(folder, originalFilename);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(r2Properties.getBucketName())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(inputStream, contentLength)
        );

        log.info("Uploaded file to R2: {}", key);
        return buildPublicUrl(key);
    }

    public String uploadBytes(byte[] data, String key, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(r2Properties.getBucketName())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data)
        );
        log.info("Uploaded bytes to R2: {}", key);
        return buildPublicUrl(key);
    }

    public void deleteFile(String key) {
        validateDeleteKey(key);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(key)
                    .build());
            log.info("Deleted file from R2: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete file from R2: {}", key, e);
        }
    }

    public String extractKeyFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String publicUrl = r2Properties.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank() && url.startsWith(publicUrl)) {
            return url.substring(publicUrl.length()).replaceAll("^/+", "");
        }

        int idx = url.indexOf(r2Properties.getBucketName());
        if (idx >= 0) {
            return url.substring(idx + r2Properties.getBucketName().length()).replaceAll("^/+", "");
        }
        return null;
    }

    private String buildTenantKey(String folder, String originalFilename) {
        String ext = extractExtension(originalFilename);
        String suffix = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return tenantId + "/" + folder + "/" + suffix;
    }

    private void validateDeleteKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key is required");
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("Invalid key");
        }

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String tenantPrefix = tenantId + "/";
        if (!key.startsWith(tenantPrefix)) {
            throw new IllegalArgumentException("Invalid key");
        }

        validateFolderInPath(key.substring(tenantPrefix.length()));
    }

    private void validateFolderInPath(String path) {
        int slashIndex = path.indexOf('/');
        if (slashIndex <= 0) {
            throw new IllegalArgumentException("Invalid key");
        }

        String folder = path.substring(0, slashIndex);
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Invalid folder in key: " + folder);
        }
    }

    private String buildPublicUrl(String key) {
        String publicUrl = r2Properties.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.replaceAll("/+$", "") + "/" + key;
        }

        return "https://" + r2Properties.getAccountId() + ".r2.cloudflarestorage.com/"
                + r2Properties.getBucketName() + "/" + key;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
