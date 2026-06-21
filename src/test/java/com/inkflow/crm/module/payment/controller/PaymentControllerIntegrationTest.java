package com.inkflow.crm.module.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.payment.dto.ProcessPaymentRequest;
import com.inkflow.crm.module.payment.dto.ProcessRefundRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

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
    void getPaymentSummary_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/payments/appointment/{id}/summary", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPaymentSummary_withOwnerAuth_returnsOk() throws Exception {
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
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        mockMvc.perform(get("/payments/appointment/{id}/summary", appointment.getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.finalPrice").value(1000));
    }

    @Test
    void processPayment_withOwnerAuth_persistsTransactionInDb() throws Exception {
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
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        ProcessPaymentRequest body = ProcessPaymentRequest.builder()
                .appointmentId(appointment.getId())
                .amount(BigDecimal.valueOf(500))
                .paymentMethod("cash")
                .build();

        mockMvc.perform(post("/payments/process")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(500));

        List<Transaction> transactions =
                transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointment.getId());
        assertEquals(1, transactions.size());
        assertEquals(BigDecimal.valueOf(500), transactions.get(0).getAmount());
        assertEquals(PaymentType.SERVICE_PAYMENT, transactions.get(0).getPaymentType());
    }

    @Test
    void processPayment_withArtistAuth_returnsForbidden() throws Exception {
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
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        ProcessPaymentRequest body = ProcessPaymentRequest.builder()
                .appointmentId(appointment.getId())
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("cash")
                .build();

        mockMvc.perform(post("/payments/process")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void processRefund_withOwnerAuth_persistsRefundInDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Appointment appointment = saveConfirmedAppointment(bundle);
        Transaction original = processPaymentAndLoadTransaction(bundle, appointment, BigDecimal.valueOf(500));

        ProcessRefundRequest refundBody = ProcessRefundRequest.builder()
                .transactionId(original.getId())
                .amount(BigDecimal.valueOf(200))
                .reason("Partial refund")
                .paymentMethod("cash")
                .build();

        mockMvc.perform(post("/payments/refund")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(200));

        Transaction reloadedOriginal = transactionRepository.findById(original.getId()).orElseThrow();
        assertEquals(BigDecimal.valueOf(200), reloadedOriginal.getRefundedAmount());

        List<Transaction> refunds = transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(
                        appointment.getId())
                .stream()
                .filter(tx -> tx.getPaymentType() == PaymentType.REFUND)
                .toList();
        assertEquals(1, refunds.size());
        assertEquals(TransactionType.EXPENSE, refunds.get(0).getType());
        assertEquals(original.getId(), refunds.get(0).getOriginalTransactionId());
    }

    @Test
    void processPayment_duplicateFullPayment_persistsBothTransactions() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Appointment appointment = saveConfirmedAppointment(bundle);
        ProcessPaymentRequest body = ProcessPaymentRequest.builder()
                .appointmentId(appointment.getId())
                .amount(BigDecimal.valueOf(500))
                .paymentMethod("cash")
                .build();

        mockMvc.perform(post("/payments/process")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/payments/process")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        List<Transaction> payments = transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(
                        appointment.getId())
                .stream()
                .filter(tx -> tx.getPaymentType() == PaymentType.SERVICE_PAYMENT)
                .toList();
        assertEquals(2, payments.size());
        assertEquals(BigDecimal.valueOf(1000),
                payments.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void processRefund_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        Appointment appointment = saveConfirmedAppointment(bundle);
        Transaction original = processPaymentAndLoadTransaction(bundle, appointment, BigDecimal.valueOf(300));

        ProcessRefundRequest refundBody = ProcessRefundRequest.builder()
                .transactionId(original.getId())
                .amount(BigDecimal.valueOf(100))
                .reason("Artist attempt")
                .paymentMethod("cash")
                .build();

        mockMvc.perform(post("/payments/refund")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundBody)))
                .andExpect(status().isForbidden());

        Transaction unchanged = transactionRepository.findById(original.getId()).orElseThrow();
        assertEquals(BigDecimal.ZERO, unchanged.getRefundedAmount());
    }

    private Appointment saveConfirmedAppointment(TenantBundle bundle) {
        return appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(bundle.location())
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());
    }

    private Transaction processPaymentAndLoadTransaction(
            TenantBundle bundle, Appointment appointment, BigDecimal amount) throws Exception {
        ProcessPaymentRequest body = ProcessPaymentRequest.builder()
                .appointmentId(appointment.getId())
                .amount(amount)
                .paymentMethod("cash")
                .build();

        mockMvc.perform(post("/payments/process")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        return transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointment.getId())
                .stream()
                .filter(tx -> tx.getPaymentType() == PaymentType.SERVICE_PAYMENT)
                .findFirst()
                .orElseThrow();
    }
}
