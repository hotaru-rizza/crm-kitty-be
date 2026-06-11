package com.inkflow.crm.module.storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.config.R2Properties;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.storage.dto.PresignedUploadRequest;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Presigner s3Presigner;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private R2Properties r2Properties;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getPresignedUrl_withoutAuth_returnsUnauthorized() throws Exception {
        PresignedUploadRequest body = new PresignedUploadRequest();
        body.setFolder("portfolio");
        body.setFilename("photo.jpg");
        body.setContentType("image/jpeg");

        mockMvc.perform(post("/files/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPresignedUrl_withOwnerAuth_returnsTenantScopedUrl() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        UUID tenantId = bundle.tenant().getId();

        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://upload.example.com/signed"));
        when(presigned.httpRequest()).thenReturn(SdkHttpRequest.builder()
                .uri("https://upload.example.com/signed")
                .method(SdkHttpMethod.PUT)
                .build());
        when(presigned.signedHeaders()).thenReturn(Map.of("Content-Type", List.of("image/jpeg")));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUploadRequest body = new PresignedUploadRequest();
        body.setFolder("portfolio");
        body.setFilename("photo.jpg");
        body.setContentType("image/jpeg");

        mockMvc.perform(post("/files/presign")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value(startsWith(tenantId + "/portfolio/")))
                .andExpect(jsonPath("$.data.fileUrl").value(containsString(tenantId + "/portfolio/")))
                .andExpect(jsonPath("$.data.fileUrl").value(startsWith("https://")))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example.com/signed"));

        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void getPresignedUrl_withInvalidFolder_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        PresignedUploadRequest body = new PresignedUploadRequest();
        body.setFolder("invalid_folder");
        body.setFilename("photo.jpg");
        body.setContentType("image/jpeg");

        mockMvc.perform(post("/files/presign")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFile_withOwnerAuth_uploadsToR2AndReturnsPublicUrl() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        UUID tenantId = bundle.tenant().getId();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/files/upload")
                        .file(file)
                        .param("folder", "portfolio")
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value(containsString(tenantId + "/portfolio/")))
                .andExpect(jsonPath("$.data.url").value(startsWith("https://")));

        verify(s3Client).putObject(
                org.mockito.ArgumentMatchers.argThat((PutObjectRequest req) ->
                        req.key().startsWith(tenantId + "/portfolio/")
                                && "image/jpeg".equals(req.contentType())),
                any(RequestBody.class));
    }

    @Test
    void deleteFile_withOwnerAuth_deletesTenantScopedKey() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        UUID tenantId = bundle.tenant().getId();
        String key = tenantId + "/gallery/photo.jpg";

        mockMvc.perform(delete("/files")
                        .param("key", key)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(s3Client).deleteObject(org.mockito.ArgumentMatchers.argThat((DeleteObjectRequest req) ->
                r2Properties.getBucketName().equals(req.bucket()) && key.equals(req.key())));
    }

    @Test
    void deleteFile_withOtherTenantKey_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(delete("/files")
                        .param("key", UUID.randomUUID() + "/gallery/photo.jpg")
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isBadRequest());
    }
}
