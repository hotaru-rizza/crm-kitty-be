package com.inkflow.crm.module.subscription.controller;

import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.SubscriptionRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "monobank.acquiring.token=REPLACE_TEST",
        "monobank.acquiring.redirect-url=http://localhost:5173/app/settings/billing",
        "monobank.acquiring.webhook-url=http://localhost:8080/api/payments/monobank/webhook"
})
class SubscriptionControllerIntegrationTest {

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

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getCurrent_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/subscription"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrent_withOwnerAuth_createsTrialSubscriptionInDatabase() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/subscription").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("TRIAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.monthlyPrice").value(399));

        var subscription = subscriptionRepository.findByTenantId(bundle.tenant().getId()).orElseThrow();
        assertEquals("TRIAL", subscription.getPlan());
        assertEquals("ACTIVE", subscription.getStatus());
        assertEquals(bundle.tenant().getId(), subscription.getTenantId());
        assertNotNull(subscription.getTrialEndsAt());
        assertTrue(subscription.getTrialEndsAt().isAfter(java.time.Instant.now()));
    }

    @Test
    void checkout_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/subscription/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkout_withOwnerAuth_returnsSandboxInvoiceAndPersistsIt() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(post("/subscription/checkout").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("STANDARD"))
                .andExpect(jsonPath("$.data.invoiceId").isNotEmpty())
                .andExpect(jsonPath("$.data.pageUrl").isNotEmpty());

        var subscription = subscriptionRepository.findByTenantId(bundle.tenant().getId()).orElseThrow();
        assertNotNull(subscription.getLastInvoiceId());
        assertTrue(subscription.getLastInvoiceId().startsWith("sandbox_sub_"));
    }
}
