package com.inkflow.crm.module.settings.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.settings.dto.UpdateUserSettingsRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class SettingsControllerIntegrationTest {

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
    void getCompanySettings_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/settings/company"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserSettings_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/settings/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserSettings_withOwnerAuth_returnsDefaults() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/settings/user").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.language").exists())
                .andExpect(jsonPath("$.data.startPage").exists());
    }

    @Test
    void patchUserSettings_withOwnerAuth_persistsToDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        UpdateUserSettingsRequest body = new UpdateUserSettingsRequest();
        body.setLanguage("ua");
        body.setStartPage("calendar");

        mockMvc.perform(patch("/settings/user")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("ua"))
                .andExpect(jsonPath("$.data.startPage").value("calendar"));

        Staff updated = staffRepository.findById(bundle.owner().getId()).orElseThrow();
        assertEquals("ua", updated.getUiLanguage());
        assertEquals("calendar", updated.getStartPage());
    }

    @Test
    void patchUserSettings_withoutAuth_returnsUnauthorized() throws Exception {
        UpdateUserSettingsRequest body = new UpdateUserSettingsRequest();
        body.setLanguage("ua");

        mockMvc.perform(patch("/settings/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRolePermissions_withOwnerAuth_returnsPermissionCatalog() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/settings/permissions").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].value").exists())
                .andExpect(jsonPath("$.data[0].category").exists());
    }

    @Test
    void getRolePermissions_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/settings/permissions").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }
}
