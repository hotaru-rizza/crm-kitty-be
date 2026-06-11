package com.inkflow.crm.module.request.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.ClientStatus;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.request.dto.ConvertRequestRequest;
import com.inkflow.crm.module.request.dto.CreateRequestRequest;
import com.inkflow.crm.module.request.dto.UpdateRequestStatusRequest;

import java.math.BigDecimal;
import java.util.UUID;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class RequestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private RequestRepository requestRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void createRequest_withoutAuth_returnsUnauthorized() throws Exception {
        CreateRequestRequest body = CreateRequestRequest.builder()
                .source("website")
                .clientName("John Doe")
                .build();

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void createRequest_withAuth_returnsCreated() throws Exception {
        TenantBundle tenant = seedTenant();
        CreateRequestRequest body = CreateRequestRequest.builder()
                .source("website")
                .clientName("John Doe")
                .build();

        mockMvc.perform(post("/requests")
                        .with(SecurityTestSupport.crmUser(tenant.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientName").value("John Doe"))
                .andExpect(jsonPath("$.data.status").value("new"));
    }

    @Test
    void createRequest_withInvalidBody_returnsBadRequest() throws Exception {
        TenantBundle tenant = seedTenant();
        CreateRequestRequest body = CreateRequestRequest.builder()
                .source("invalid-source")
                .clientName("")
                .build();

        mockMvc.perform(post("/requests")
                        .with(SecurityTestSupport.crmUser(tenant.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateRequestStatus_withOwnerAuth_persistsStatusInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Jane Prospect");

        UpdateRequestStatusRequest body = UpdateRequestStatusRequest.builder()
                .status("replied")
                .build();

        mockMvc.perform(patch("/requests/{id}/status", requestId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("replied"));

        Request persisted = requestRepository.findByIdAndTenantId(requestId, bundle.tenant().getId())
                .orElseThrow();
        assertEquals(RequestStatus.REPLIED, persisted.getStatus());
        assertNotNull(persisted.getRepliedAt());
    }

    @Test
    void convertToClient_withOwnerAuth_createsClientAndMarksRequestConverted() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Convert Me");

        ConvertRequestRequest body = ConvertRequestRequest.builder()
                .firstName("Convert")
                .lastName("Me")
                .phone("+380671112233")
                .build();

        mockMvc.perform(post("/requests/{id}/convert", requestId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").value("+380671112233"));

        Request persistedRequest = requestRepository.findByIdAndTenantId(requestId, bundle.tenant().getId())
                .orElseThrow();
        assertEquals(RequestStatus.CONVERTED, persistedRequest.getStatus());
        assertNotNull(persistedRequest.getConvertedAt());
        assertNotNull(persistedRequest.getConvertedClient());

        Client persistedClient = clientRepository
                .findByPhoneAndTenantIdAndDeletedAtIsNull("+380671112233", bundle.tenant().getId())
                .orElseThrow();
        assertEquals("Convert", persistedClient.getFirstName());
        assertEquals(ClientStatus.ACTIVE, persistedClient.getStatus());
    }

    @Test
    void getAllRequests_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getAllRequests_withOwnerAuth_returnsCreatedRequest() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Listed Prospect");

        mockMvc.perform(get("/requests")
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$.data[0].clientName").value("Listed Prospect"))
                .andExpect(jsonPath("$.data[0].status").value("new"));
    }

    @Test
    void getRequestById_withOwnerAuth_returnsRequestDetails() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Detail Prospect");

        mockMvc.perform(get("/requests/{id}", requestId)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(requestId.toString()))
                .andExpect(jsonPath("$.data.clientName").value("Detail Prospect"))
                .andExpect(jsonPath("$.data.source").value("website"));
    }

    @Test
    void getRequestById_fromOtherTenant_returnsNotFound() throws Exception {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        UUID requestId = createRequest(tenantB, "Foreign Request");

        mockMvc.perform(get("/requests/{id}", requestId)
                        .with(crmUser(tenantA.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void deleteRequest_withOwnerAuth_removesFromDb() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Delete Me");

        mockMvc.perform(delete("/requests/{id}", requestId)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(requestRepository.findByIdAndTenantId(requestId, bundle.tenant().getId()).isEmpty());
    }

    @Test
    void updateRequestStatus_toSpam_persistsSpamWithoutRepliedAt() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Spam Candidate");

        UpdateRequestStatusRequest body = UpdateRequestStatusRequest.builder()
                .status("spam")
                .build();

        mockMvc.perform(patch("/requests/{id}/status", requestId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("spam"));

        Request persisted = requestRepository.findByIdAndTenantId(requestId, bundle.tenant().getId())
                .orElseThrow();
        assertEquals(RequestStatus.SPAM, persisted.getStatus());
        assertNull(persisted.getRepliedAt());
    }

    @Test
    void convertToClient_whenPhoneAlreadyExists_returnsConflict() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID requestId = createRequest(bundle, "Duplicate Phone");
        String existingPhone = "+380671112233";

        clientRepository.save(Client.builder()
                .tenantId(bundle.tenant().getId())
                .firstName("Existing")
                .lastName("Client")
                .phone(existingPhone)
                .status(ClientStatus.ACTIVE)
                .totalVisits(0)
                .cancelledVisits(0)
                .ltv(BigDecimal.ZERO)
                .build());

        ConvertRequestRequest body = ConvertRequestRequest.builder()
                .firstName("Duplicate")
                .lastName("Phone")
                .phone(existingPhone)
                .build();

        mockMvc.perform(post("/requests/{id}/convert", requestId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.error.code").value("PHONE_ALREADY_EXISTS"));

        Request persistedRequest = requestRepository.findByIdAndTenantId(requestId, bundle.tenant().getId())
                .orElseThrow();
        assertEquals(RequestStatus.NEW, persistedRequest.getStatus());
    }

    @Test
    void updateRequestStatus_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        UUID requestId = createRequest(bundle, "Protected Request");

        UpdateRequestStatusRequest body = UpdateRequestStatusRequest.builder()
                .status("spam")
                .build();

        mockMvc.perform(patch("/requests/{id}/status", requestId)
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    private UUID createRequest(TenantBundle bundle, String clientName) throws Exception {
        CreateRequestRequest body = CreateRequestRequest.builder()
                .source("website")
                .clientName(clientName)
                .build();

        String createResponse = mockMvc.perform(post("/requests")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository,
                staffRepository,
                clientRepository,
                serviceRepository,
                locationRepository
        );
    }
}
