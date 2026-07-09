package com.inkflow.crm.module.google.controller;

import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class GoogleCalendarControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void getStatus_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/staff/{id}/google/status", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStatus_withOwnerAuth_returnsDisconnected() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/staff/{id}/google/status", bundle.owner().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    void getStatus_withConnectedStaff_returnsConnected() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        bundle.owner().setGoogleRefreshToken("refresh-token");
        bundle.owner().setGoogleCalendarEmail("owner@gmail.com");
        staffRepository.save(bundle.owner());

        mockMvc.perform(get("/staff/{id}/google/status", bundle.owner().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.email").value("owner@gmail.com"))
                .andExpect(jsonPath("$.data.configured").value(true));
    }

    @Test
    void getAuthUrl_withOwnerAuth_returnsAuthorizationUrl() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/staff/{id}/google/auth-url", bundle.owner().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").isNotEmpty());
    }

    @Test
    void disconnect_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/staff/{id}/google/disconnect", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disconnect_withOwnerAuth_clearsConnection() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        bundle.owner().setGoogleRefreshToken("refresh-token");
        bundle.owner().setGoogleAccessToken("access-token");
        bundle.owner().setGoogleCalendarId("primary");
        bundle.owner().setGoogleCalendarEmail("owner@gmail.com");
        staffRepository.save(bundle.owner());

        mockMvc.perform(delete("/staff/{id}/google/disconnect", bundle.owner().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/staff/{id}/google/status", bundle.owner().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    void getStatus_withArtistAuth_returnsOwnStatus() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff/{id}/google/status", artist.getId())
                        .with(crmUser(artist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    void getStatus_withArtistAuth_returnsForbiddenForOtherStaff() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff/{id}/google/status", bundle.owner().getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void getAuthUrl_withArtistAuth_returnsOwnAuthorizationUrl() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff/{id}/google/auth-url", artist.getId())
                        .with(crmUser(artist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").isNotEmpty());
    }

    @Test
    void getAuthUrl_withArtistAuth_returnsForbiddenForOtherStaff() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff/{id}/google/auth-url", bundle.owner().getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void disconnect_withArtistAuth_disconnectsOwnCalendar() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        artist.setGoogleRefreshToken("refresh-token");
        artist.setGoogleAccessToken("access-token");
        artist.setGoogleCalendarId("primary");
        artist.setGoogleCalendarEmail("artist@gmail.com");
        staffRepository.save(artist);

        mockMvc.perform(delete("/staff/{id}/google/disconnect", artist.getId())
                        .with(crmUser(artist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void disconnect_withArtistAuth_returnsForbiddenForOtherStaff() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(delete("/staff/{id}/google/disconnect", bundle.owner().getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void getStatus_forOtherTenantStaff_returnsNotFound() throws Exception {
        TenantBundle tenantA = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        TenantBundle tenantB = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/staff/{id}/google/status", tenantB.owner().getId())
                        .with(crmUser(tenantA.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("STAFF_NOT_FOUND"));
    }
}
