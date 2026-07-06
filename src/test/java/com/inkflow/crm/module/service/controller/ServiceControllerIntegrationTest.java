package com.inkflow.crm.module.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.service.dto.CreateServiceRequest;
import com.inkflow.crm.module.service.dto.UpdateServiceRequest;
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

import java.math.BigDecimal;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ServiceControllerIntegrationTest {

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

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllServices_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createService_withOwnerAuth_persistsInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateServiceRequest body = CreateServiceRequest.builder()
                .title("Consultation")
                .pricingType("fixed")
                .price(BigDecimal.valueOf(300))
                .duration(30)
                .color("#6366f1")
                .build();

        String response = mockMvc.perform(post("/services")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Consultation"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String serviceId = objectMapper.readTree(response).path("data").path("id").asText();
        Service saved = serviceRepository.findById(java.util.UUID.fromString(serviceId)).orElseThrow();
        assertEquals(bundle.tenant().getId(), saved.getTenantId());
        assertEquals("Consultation", saved.getTitle());
        assertEquals(BigDecimal.valueOf(300), saved.getPrice());
    }

    @Test
    void updateService_withOwnerAuth_updatesDb() throws Exception {
        TenantBundle bundle = seedTenant();

        UpdateServiceRequest body = UpdateServiceRequest.builder()
                .title("Premium Session")
                .price(BigDecimal.valueOf(1500))
                .build();

        mockMvc.perform(patch("/services/{id}", bundle.service().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Premium Session"));

        Service updated = serviceRepository.findById(bundle.service().getId()).orElseThrow();
        assertEquals("Premium Session", updated.getTitle());
        assertEquals(BigDecimal.valueOf(1500), updated.getPrice());
    }

    @Test
    void updateService_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateServiceRequest body = UpdateServiceRequest.builder()
                .title("Blocked Update")
                .build();

        mockMvc.perform(patch("/services/{id}", bundle.service().getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void createService_withInvalidBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateServiceRequest body = CreateServiceRequest.builder()
                .title("X")
                .pricingType("invalid")
                .price(BigDecimal.valueOf(-10))
                .duration(5)
                .color("not-a-color")
                .build();

        mockMvc.perform(post("/services")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getService_withOwnerAuth_returnsDetail() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/services/{id}", bundle.service().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bundle.service().getId().toString()))
                .andExpect(jsonPath("$.data.title").value("Tattoo Session"));
    }

    @Test
    void deleteService_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(delete("/services/{id}", bundle.service().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Service deleted = serviceRepository.findById(bundle.service().getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void createService_hourlyWithoutDuration_persistsWithDefaultSlot() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateServiceRequest body = CreateServiceRequest.builder()
                .title("Removal")
                .pricingType("hourly")
                .price(BigDecimal.valueOf(400))
                .color("#6366f1")
                .build();

        String response = mockMvc.perform(post("/services")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pricingType").value("hourly"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String serviceId = objectMapper.readTree(response).path("data").path("id").asText();
        Service saved = serviceRepository.findById(java.util.UUID.fromString(serviceId)).orElseThrow();
        assertEquals(60, saved.getDuration());
    }

    @Test
    void deleteService_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(delete("/services/{id}", bundle.service().getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void createService_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        CreateServiceRequest body = CreateServiceRequest.builder()
                .title("Blocked Service")
                .pricingType("fixed")
                .price(BigDecimal.valueOf(100))
                .duration(30)
                .color("#6366f1")
                .build();

        mockMvc.perform(post("/services")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void getServicePrice_withOwnerAuth_returnsBasePrice() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/services/{id}/price", bundle.service().getId())
                        .param("artistId", bundle.owner().getId().toString())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceId").value(bundle.service().getId().toString()))
                .andExpect(jsonPath("$.data.price").value(1000))
                .andExpect(jsonPath("$.data.isOverride").value(false));
    }

    @Test
    void getService_fromOtherTenant_returnsNotFound() throws Exception {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        mockMvc.perform(get("/services/{id}", tenantB.service().getId())
                        .with(crmUser(tenantA.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("SERVICE_NOT_FOUND"));
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
