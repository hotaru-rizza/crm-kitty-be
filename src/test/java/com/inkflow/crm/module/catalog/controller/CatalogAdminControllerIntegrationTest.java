package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.catalog.dto.CatalogRetagResultDto;
import com.inkflow.crm.module.catalog.dto.CatalogSeedResultDto;
import com.inkflow.crm.module.catalog.service.TattooCatalogService;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class CatalogAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TattooCatalogService tattooCatalogService;

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
    void seed_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/catalog/admin/tattoos/seed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(tattooCatalogService, never()).seed();
    }

    @Test
    void retag_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/catalog/admin/tattoos/retag"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(tattooCatalogService, never()).retag();
    }

    @Test
    void seed_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(post("/catalog/admin/tattoos/seed").with(crmUser(artist)))
                .andExpect(status().isForbidden());

        verify(tattooCatalogService, never()).seed();
    }

    @Test
    void retag_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(post("/catalog/admin/tattoos/retag").with(crmUser(artist)))
                .andExpect(status().isForbidden());

        verify(tattooCatalogService, never()).retag();
    }

    @Test
    void seed_withOwnerAuth_returnsSeedResult() throws Exception {
        TenantBundle bundle = seedTenant();
        when(tattooCatalogService.seed()).thenReturn(new CatalogSeedResultDto(5, 42L));

        mockMvc.perform(post("/catalog/admin/tattoos/seed").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.saved").value(5))
                .andExpect(jsonPath("$.data.total").value(42));

        verify(tattooCatalogService).seed();
    }

    @Test
    void retag_withOwnerAuth_returnsRetagResult() throws Exception {
        TenantBundle bundle = seedTenant();
        when(tattooCatalogService.retag()).thenReturn(new CatalogRetagResultDto(12));

        mockMvc.perform(post("/catalog/admin/tattoos/retag").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.retagged").value(12));

        verify(tattooCatalogService).retag();
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
