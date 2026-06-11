package com.inkflow.crm.module.auth.controller;

import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.enums.UserRole;
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

import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

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
    void getCurrentUser_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUser_withOwnerAuth_returnsProfile() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/auth/me").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(bundle.owner().getId().toString()))
                .andExpect(jsonPath("$.data.role").value("owner"))
                .andExpect(jsonPath("$.data.tenantName").value(bundle.tenant().getName()))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void getCurrentUser_withArtistAuth_returnsProfile() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        bundle.owner().setAuthUserId(UUID.randomUUID().toString());
        artist.setAuthUserId(UUID.randomUUID().toString());
        staffRepository.save(bundle.owner());
        staffRepository.save(artist);

        mockMvc.perform(get("/auth/me").with(crmUser(artist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(artist.getId().toString()))
                .andExpect(jsonPath("$.data.role").value("artist"))
                .andExpect(jsonPath("$.data.email").value(artist.getEmail()))
                .andExpect(jsonPath("$.data.tenantId").value(bundle.tenant().getId().toString()))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void getCurrentUser_withUnknownStaffId_returnsNotFound() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        bundle.owner().setAuthUserId(UUID.randomUUID().toString());
        staffRepository.save(bundle.owner());

        mockMvc.perform(get("/auth/me")
                        .with(crmUser(UUID.randomUUID(), bundle.tenant().getId(), UserRole.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("STAFF_NOT_FOUND"));
    }
}
