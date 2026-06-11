package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.service.PortfolioService;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PortfolioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

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
    void getPortfolio_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/staff/{staffId}/portfolio", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPortfolio_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff/{staffId}/portfolio", bundle.owner().getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden());

        verify(portfolioService, never()).getPortfolio(bundle.owner().getId());
    }

    @Test
    void getPortfolio_withOwnerAuth_returnsPortfolioPayload() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID staffId = bundle.owner().getId();

        when(portfolioService.getPortfolio(staffId)).thenReturn(List.of(
                new TattooDto(
                        1L,
                        staffId,
                        "active",
                        "https://cdn.example.com/tattoo.jpg",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("traditional"),
                        true
                )
        ));

        mockMvc.perform(get("/staff/{staffId}/portfolio", staffId)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].staffId").value(staffId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://cdn.example.com/tattoo.jpg"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("traditional"))
                .andExpect(jsonPath("$.data[0].showcase").value(true));

        ArgumentCaptor<UUID> staffIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(portfolioService).getPortfolio(staffIdCaptor.capture());
        assertEquals(staffId, staffIdCaptor.getValue());
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
