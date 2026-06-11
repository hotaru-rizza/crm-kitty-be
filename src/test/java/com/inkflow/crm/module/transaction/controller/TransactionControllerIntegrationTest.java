package com.inkflow.crm.module.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.transaction.dto.CreateTransactionRequest;
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
import java.time.Instant;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerIntegrationTest {

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

    @Autowired
    private TransactionRepository transactionRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllTransactions_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllTransactions_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/transactions").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getAllTransactions_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/transactions").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTransaction_withOwnerAuth_persistsInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        CreateTransactionRequest body = validCreateRequest(bundle);

        String response = mockMvc.perform(post("/transactions")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(2500))
                .andExpect(jsonPath("$.data.type").value("income"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID transactionId = UUID.fromString(
                objectMapper.readTree(response).path("data").path("id").asText());

        var persisted = transactionRepository.findById(transactionId).orElseThrow();
        assertEquals(bundle.tenant().getId(), persisted.getTenantId());
        assertEquals(BigDecimal.valueOf(2500), persisted.getAmount());
        assertEquals(TransactionType.INCOME, persisted.getType());
    }

    @Test
    void getTransactionById_withOwnerAuth_returnsTransaction() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID transactionId = createTransactionAndGetId(bundle);

        mockMvc.perform(get("/transactions/{id}", transactionId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.data.amount").value(2500))
                .andExpect(jsonPath("$.data.type").value("income"));
    }

    @Test
    void deleteTransaction_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID transactionId = createTransactionAndGetId(bundle);

        mockMvc.perform(delete("/transactions/{id}", transactionId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var deleted = transactionRepository.findById(transactionId).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void createTransaction_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(post("/transactions")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest(bundle))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTransaction_withInvalidBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(post("/transactions")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private UUID createTransactionAndGetId(TenantBundle bundle) throws Exception {
        String response = mockMvc.perform(post("/transactions")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest(bundle))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    private CreateTransactionRequest validCreateRequest(TenantBundle bundle) {
        return CreateTransactionRequest.builder()
                .type("income")
                .category("service")
                .amount(BigDecimal.valueOf(2500))
                .paymentMethod("cash")
                .locationId(bundle.location().getId())
                .date(Instant.parse("2025-06-01T10:00:00Z"))
                .description("Session payment")
                .build();
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
    }
}
