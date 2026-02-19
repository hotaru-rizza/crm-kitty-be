package com.inkflow.crm.module.storage.service;

import com.inkflow.crm.config.R2Properties;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties r2Properties;

    /**
     * Generate a presigned PUT URL — browser uploads directly to R2
     * No file passes through our server
     */
    public PresignedUploadResult generatePresignedUploadUrl(String folder, String originalFilename, String contentType) {
        String ext = extractExtension(originalFilename);
        String key = folder + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

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

    /**
     * Upload file through backend (for small files like avatars)
     */
    public String uploadFile(String folder, String originalFilename, String contentType, InputStream inputStream, long contentLength) {
        String ext = extractExtension(originalFilename);
        String key = folder + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

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

    /**
     * Delete a file from R2 by its key
     */
    public void deleteFile(String key) {
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

    /**
     * Extract key from a full public URL
     */
    public String extractKeyFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String publicUrl = r2Properties.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank() && url.startsWith(publicUrl)) {
            return url.substring(publicUrl.length()).replaceAll("^/+", "");
        }
        // Fallback: extract path after bucket name
        int idx = url.indexOf(r2Properties.getBucketName());
        if (idx >= 0) {
            return url.substring(idx + r2Properties.getBucketName().length()).replaceAll("^/+", "");
        }
        return null;
    }

    private String buildPublicUrl(String key) {
        String publicUrl = r2Properties.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.replaceAll("/+$", "") + "/" + key;
        }
        // Return R2 dev URL if public URL is not configured
        return "https://" + r2Properties.getAccountId() + ".r2.cloudflarestorage.com/"
                + r2Properties.getBucketName() + "/" + key;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    public record PresignedUploadResult(
            String key,
            String uploadUrl,
            String method,
            Map<String, java.util.List<String>> headers,
            String fileUrl
    ) {}
}
