package com.inkflow.crm.module.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.module.finance.dto.CategoryConfigUpsertRequest;
import com.inkflow.crm.module.finance.service.CategoryConfigService;
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

import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class CategoryConfigControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryConfigService categoryConfigService;

    @Autowired
    private TransactionCategoryConfigRepository categoryConfigRepository;

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
    void getAll_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/finance/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_withOwnerAuth_returnsCategories() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        categoryConfigService.ensureDefaults(bundle.tenant().getId());

        mockMvc.perform(get("/finance/categories").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[?(@.categoryKey == 'service')].plType").value("INCOME"))
                .andExpect(jsonPath("$.data[?(@.categoryKey == 'rent')].plType").value("EXPENSE"))
                .andExpect(jsonPath("$.data[?(@.categoryKey == 'service')].isDefault").value(true));
    }

    @Test
    void getAll_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/finance/categories").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_withOwnerAuth_persistsInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        CategoryConfigUpsertRequest body = new CategoryConfigUpsertRequest();
        body.setLabel("Marketing");
        body.setColor("#a855f7");
        body.setPlType("EXPENSE");

        String response = mockMvc.perform(post("/finance/categories")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("Marketing"))
                .andExpect(jsonPath("$.data.plType").value("EXPENSE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID configId = UUID.fromString(
                objectMapper.readTree(response).path("data").path("id").asText());

        var persisted = categoryConfigRepository.findById(configId).orElseThrow();
        assertEquals(bundle.tenant().getId(), persisted.getTenantId());
        assertEquals("Marketing", persisted.getLabel());
        assertEquals("#a855f7", persisted.getColor());
        assertEquals("EXPENSE", persisted.getPlType());
    }

    @Test
    void upsertCategory_withOwnerAuth_updatesDb() throws Exception {
        TenantBundle bundle = seedTenant();
        CategoryConfigUpsertRequest body = new CategoryConfigUpsertRequest();
        body.setLabel("Custom Rent");
        body.setColor("#f97316");
        body.setPlType("EXPENSE");

        mockMvc.perform(put("/finance/categories/{key}", "custom_rent")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryKey").value("custom_rent"))
                .andExpect(jsonPath("$.data.label").value("Custom Rent"));

        var persisted = categoryConfigRepository
                .findByCategoryKeyAndDeletedAtIsNull("custom_rent")
                .orElseThrow();
        assertEquals("Custom Rent", persisted.getLabel());
        assertEquals("#f97316", persisted.getColor());
        assertEquals("EXPENSE", persisted.getPlType());
    }

    @Test
    void deleteCategory_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        CategoryConfigUpsertRequest body = new CategoryConfigUpsertRequest();
        body.setLabel("Deletable");
        body.setColor("#111111");
        body.setPlType("EXPENSE");

        String createResponse = mockMvc.perform(post("/finance/categories")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID configId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        mockMvc.perform(delete("/finance/categories/{id}", configId)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var deleted = categoryConfigRepository.findById(configId).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void createCategory_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        CategoryConfigUpsertRequest body = new CategoryConfigUpsertRequest();
        body.setLabel("Blocked");
        body.setColor("#000000");
        body.setPlType("EXPENSE");

        mockMvc.perform(post("/finance/categories")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
    }
}
