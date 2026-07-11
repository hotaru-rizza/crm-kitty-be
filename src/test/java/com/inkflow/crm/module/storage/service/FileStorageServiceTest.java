package com.inkflow.crm.module.storage.service;

import com.inkflow.crm.config.R2Properties;
import com.inkflow.crm.module.storage.dto.PresignedDownloadResult;
import com.inkflow.crm.module.storage.dto.PresignedUploadResult;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private R2Properties r2Properties;

    @InjectMocks
    private FileStorageService fileStorageService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void extractKeyFromUrl_parsesPublicUrlPrefix() {
        whenPublicUrl("https://cdn.example.com");

        String key = fileStorageService.extractKeyFromUrl("https://cdn.example.com/tenant/gallery/file.jpg");

        assertEquals("tenant/gallery/file.jpg", key);
    }

    @Test
    void generatePresignedUploadUrl_buildsTenantScopedKeyAndPublicUrl() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        when(r2Properties.getBucketName()).thenReturn("test-bucket");
        whenPublicUrl("https://cdn.example.com");

        SdkHttpRequest httpRequest = SdkHttpRequest.builder()
                .uri("https://upload.example.com")
                .method(SdkHttpMethod.PUT)
                .build();
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://upload.example.com/signed"));
        when(presigned.httpRequest()).thenReturn(httpRequest);
        when(presigned.signedHeaders()).thenReturn(Map.of("Content-Type", List.of("image/jpeg")));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUploadResult result = fileStorageService.generatePresignedUploadUrl(
                "portfolio", "photo.jpg", "image/jpeg");

        assertTrue(result.key().startsWith(tenantId + "/portfolio/"));
        assertTrue(result.key().endsWith(".jpg"));
        assertEquals("https://cdn.example.com/" + result.key(), result.fileUrl());
        assertEquals("PUT", result.method());
        assertEquals("https://upload.example.com/signed", result.uploadUrl());
    }

    @Test
    void uploadFile_putsObjectAndReturnsPublicUrl() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        when(r2Properties.getBucketName()).thenReturn("test-bucket");
        whenPublicUrl("https://cdn.example.com");
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = fileStorageService.uploadFile(
                "gallery", "pic.png", "image/png",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3);

        assertTrue(url.startsWith("https://cdn.example.com/" + tenantId + "/gallery/"));
        assertTrue(url.endsWith(".png"));
        verify(s3Client).putObject(
                org.mockito.ArgumentMatchers.argThat((PutObjectRequest req) ->
                        "test-bucket".equals(req.bucket())
                                && req.key().startsWith(tenantId + "/gallery/")
                                && "image/png".equals(req.contentType())),
                any(RequestBody.class));
    }

    @Test
    void generatePresignedDownloadUrl_returnsSignedUrl() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        when(r2Properties.getBucketName()).thenReturn("test-bucket");
        when(r2Properties.getSignedDownloadTtlMinutes()).thenReturn(30);

        String key = tenantId + "/gallery/file.jpg";
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://download.example.com/signed"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        PresignedDownloadResult result = fileStorageService.generatePresignedDownloadUrl(key);

        assertEquals(key, result.getKey());
        assertEquals("https://download.example.com/signed", result.getDownloadUrl());
        assertTrue(result.getExpiresAt().isAfter(java.time.Instant.now()));
    }

    @Test
    void deleteFile_deletesWhenKeyIsValid() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        when(r2Properties.getBucketName()).thenReturn("test-bucket");
        String key = tenantId + "/gallery/file.jpg";

        fileStorageService.deleteFile(key);

        verify(s3Client).deleteObject(DeleteObjectRequest.builder()
                .bucket("test-bucket")
                .key(key)
                .build());
    }

    @Test
    void deleteFile_rejectsKeyWithoutTenantPrefix() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.deleteFile("other-tenant/gallery/file.jpg"));
    }

    @Test
    void deleteFile_rejectsDisallowedFolder() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.deleteFile(tenantId + "/secrets/file.jpg"));
    }

    @Test
    void deleteFile_rejectsPathTraversal() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.deleteFile(tenantId + "/gallery/../secrets/file.jpg"));
    }

    private void whenPublicUrl(String url) {
        when(r2Properties.getPublicUrl()).thenReturn(url);
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
