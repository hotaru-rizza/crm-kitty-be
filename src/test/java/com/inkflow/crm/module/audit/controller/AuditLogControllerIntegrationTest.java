package com.inkflow.crm.module.audit.controller;

import com.inkflow.crm.domain.entity.AuditLogEntry;
import com.inkflow.crm.domain.repository.AuditLogRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AuditLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

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
    void getLog_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/audit-log"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLog_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/audit-log").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLog_withOwnerAuth_returnsPersistedEntries() throws Exception {
        TenantBundle bundle = seedTenant();

        auditLogRepository.save(AuditLogEntry.builder()
                .tenantId(bundle.tenant().getId())
                .actorId(bundle.owner().getId())
                .actorName("Owner User")
                .action("CREATE")
                .entityType("CLIENT")
                .entityId(bundle.client().getId().toString())
                .entityLabel("Client One")
                .build());

        assertEquals(1, auditLogRepository.findAll().size());

        mockMvc.perform(get("/audit-log").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data[0].entityType").value("CLIENT"))
                .andExpect(jsonPath("$.data[0].entityId").value(bundle.client().getId().toString()))
                .andExpect(jsonPath("$.data[0].entityLabel").value("Client One"))
                .andExpect(jsonPath("$.data[0].actorId").value(bundle.owner().getId().toString()));
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
    }
}
