package com.inkflow.crm.module.monobank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.MonobankInvoice;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.MonobankInvoiceRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.monobank.dto.CreateOnlineInvoiceRequest;
import com.inkflow.crm.module.monobank.dto.MonobankWebhookPayload;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "monobank.acquiring.token=REPLACE_TEST",
        "monobank.acquiring.redirect-url=http://localhost:5173/app/calendar",
        "monobank.acquiring.webhook-url=http://localhost:8080/api/payments/monobank/webhook"
})
class MonobankControllerIntegrationTest {

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
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MonobankInvoiceRepository invoiceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void webhook_processesSuccessfulPaymentWithoutAuth() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(bundle.location())
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.CONFIRMED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        invoiceRepository.save(MonobankInvoice.builder()
                .tenantId(bundle.tenant().getId())
                .appointmentId(appointment.getId())
                .monobankInvoiceId("inv-integration-1")
                .amount(BigDecimal.valueOf(500))
                .pageUrl("https://pay.monobank.ua/sandbox/inv-integration-1")
                .status("pending")
                .paymentType("deposit")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build());

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-integration-1");
        payload.setStatus("success");
        payload.setAmount(50_000L);

        mockMvc.perform(post("/payments/monobank/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        MonobankInvoice updated = invoiceRepository.findByMonobankInvoiceId("inv-integration-1").orElseThrow();
        assertEquals("success", updated.getStatus());
        assertNotNull(updated.getPaidAt());
        assertNotNull(updated.getTransactionId());

        var transactions = transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointment.getId());
        assertEquals(1, transactions.size());
        assertEquals(BigDecimal.valueOf(500), transactions.get(0).getAmount());
        assertEquals(PaymentType.DEPOSIT, transactions.get(0).getPaymentType());
        assertEquals(BigDecimal.valueOf(500), appointmentRepository.findById(appointment.getId()).orElseThrow().getPrepayment());
    }

    @Test
    void createInvoice_withoutAuth_returnsUnauthorized() throws Exception {
        CreateOnlineInvoiceRequest body = new CreateOnlineInvoiceRequest();
        body.setAppointmentId(UUID.randomUUID());
        body.setAmount(BigDecimal.valueOf(100));
        body.setPaymentType("deposit");

        mockMvc.perform(post("/payments/monobank/invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInvoice_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(bundle.location())
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.CONFIRMED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        CreateOnlineInvoiceRequest body = new CreateOnlineInvoiceRequest();
        body.setAppointmentId(appointment.getId());
        body.setAmount(BigDecimal.valueOf(100));
        body.setPaymentType("deposit");

        mockMvc.perform(post("/payments/monobank/invoice")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInvoice_withInvalidAmount_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateOnlineInvoiceRequest body = new CreateOnlineInvoiceRequest();
        body.setAppointmentId(UUID.randomUUID());
        body.setAmount(BigDecimal.valueOf(0.5));
        body.setPaymentType("deposit");

        mockMvc.perform(post("/payments/monobank/invoice")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvoice_withOwnerAuth_persistsPendingInvoice() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(bundle.location())
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.CONFIRMED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        CreateOnlineInvoiceRequest body = new CreateOnlineInvoiceRequest();
        body.setAppointmentId(appointment.getId());
        body.setAmount(BigDecimal.valueOf(250));
        body.setPaymentType("service_payment");

        mockMvc.perform(post("/payments/monobank/invoice")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.amount").value(250))
                .andExpect(jsonPath("$.data.appointmentId").value(appointment.getId().toString()));

        Optional<MonobankInvoice> invoice = invoiceRepository.findByAppointmentIdAndStatus(appointment.getId(), "pending");
        assertTrue(invoice.isPresent());
        assertEquals("service_payment", invoice.get().getPaymentType());
        assertNotNull(invoice.get().getMonobankInvoiceId());
    }
}
