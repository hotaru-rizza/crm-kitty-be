package com.inkflow.crm.module.analytics;

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

import java.time.Instant;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsControllerIntegrationTest {

    private static final Instant RANGE_FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant RANGE_TO = Instant.parse("2026-06-30T23:59:59Z");

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
    void getPnl_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/analytics/pnl")
                        .param("from", RANGE_FROM.toString())
                        .param("to", RANGE_TO.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPnl_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/analytics/pnl")
                        .with(crmUser(artist))
                        .param("from", RANGE_FROM.toString())
                        .param("to", RANGE_TO.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPnl_withFromAfterTo_returnsZeroMetrics() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/analytics/pnl")
                        .with(crmUser(bundle.owner()))
                        .param("from", RANGE_TO.toString())
                        .param("to", RANGE_FROM.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revenue").value(0))
                .andExpect(jsonPath("$.data.grossProfit").value(0))
                .andExpect(jsonPath("$.data.netProfit").value(0));
    }

    @Test
    void getPnl_withOwnerAuth_returnsZeroMetricsForEmptyTenant() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/analytics/pnl")
                        .with(crmUser(bundle.owner()))
                        .param("from", RANGE_FROM.toString())
                        .param("to", RANGE_TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revenue").value(0))
                .andExpect(jsonPath("$.data.costOfSales").value(0))
                .andExpect(jsonPath("$.data.grossProfit").value(0))
                .andExpect(jsonPath("$.data.staffCommissions").value(0))
                .andExpect(jsonPath("$.data.otherExpenses").value(0))
                .andExpect(jsonPath("$.data.netProfit").value(0));
    }

    @Test
    void getAppointmentAnalytics_withOwnerAuth_returnsZeroMetricsForEmptyTenant() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/analytics/appointments")
                        .with(crmUser(bundle.owner()))
                        .param("from", RANGE_FROM.toString())
                        .param("to", RANGE_TO.toString())
                        .param("groupBy", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAppointments").value(0))
                .andExpect(jsonPath("$.data.completedAppointments").value(0))
                .andExpect(jsonPath("$.data.cancelledAppointments").value(0))
                .andExpect(jsonPath("$.data.totalRevenue").value(0))
                .andExpect(jsonPath("$.data.newClients").value(0));
    }

    @Test
    void getStaffPerformance_withOwnerAuth_returnsEmptyArrayForEmptyTenant() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/analytics/staff")
                        .with(crmUser(bundle.owner()))
                        .param("from", RANGE_FROM.toString())
                        .param("to", RANGE_TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getClientAnalytics_withOwnerAuth_returnsZeroMetricsForEmptyTenant() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/analytics/clients")
                        .with(crmUser(bundle.owner()))
                        .param("from", RANGE_FROM.toString())
                        .param("to", RANGE_TO.toString())
                        .param("groupBy", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUniqueClients").value(0))
                .andExpect(jsonPath("$.data.newClients").value(0))
                .andExpect(jsonPath("$.data.returningClients").value(0))
                .andExpect(jsonPath("$.data.repeatRate").value(0.0));
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
    }
}
