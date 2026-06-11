package com.inkflow.crm.module.monobank.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.MonobankInvoice;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.MonobankInvoiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.monobank.config.MonobankConfig;
import com.inkflow.crm.module.monobank.dto.CreateOnlineInvoiceRequest;
import com.inkflow.crm.module.monobank.dto.MonobankWebhookPayload;
import com.inkflow.crm.module.monobank.dto.OnlineInvoiceDto;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import com.inkflow.crm.support.SecurityTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonobankServiceTest {

    @Mock
    private MonobankConfig config;

    @Mock
    private MonobankInvoiceRepository invoiceRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private MonobankService monobankService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void handleWebhook_ignoresMissingInvoiceId() {
        monobankService.handleWebhook(new MonobankWebhookPayload());

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleWebhook_ignoresUnknownInvoice() {
        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("unknown");
        payload.setStatus("success");

        when(invoiceRepository.findByMonobankInvoiceId("unknown")).thenReturn(Optional.empty());

        monobankService.handleWebhook(payload);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleWebhook_recordsSuccessfulDeposit() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        MonobankInvoice invoice = MonobankInvoice.builder()
                .tenantId(tenantId)
                .appointmentId(appointmentId)
                .monobankInvoiceId("inv-1")
                .amount(BigDecimal.valueOf(500))
                .status("pending")
                .paymentType("deposit")
                .build();

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .prepayment(BigDecimal.ZERO)
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-1");
        payload.setStatus("success");
        payload.setAmount(50_000L);

        when(invoiceRepository.findByMonobankInvoiceId("inv-1")).thenReturn(Optional.of(invoice));
        when(config.getToken()).thenReturn("REPLACE_TEST");
        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId)).thenReturn(Optional.of(appointment));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        monobankService.handleWebhook(payload);

        assertEquals("success", invoice.getStatus());
        assertNotNull(invoice.getPaidAt());
        assertNotNull(invoice.getTransactionId());
        assertEquals(BigDecimal.valueOf(500), appointment.getPrepayment());
        verify(invoiceRepository).save(invoice);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void handleWebhook_rejectsAmountMismatch() {
        MonobankInvoice invoice = MonobankInvoice.builder()
                .monobankInvoiceId("inv-2")
                .amount(BigDecimal.valueOf(500))
                .status("pending")
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-2");
        payload.setStatus("success");
        payload.setAmount(10_000L);

        when(invoiceRepository.findByMonobankInvoiceId("inv-2")).thenReturn(Optional.of(invoice));

        monobankService.handleWebhook(payload);

        verify(transactionRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleWebhook_skipsAlreadyProcessedInvoice() {
        UUID transactionId = UUID.randomUUID();
        MonobankInvoice invoice = MonobankInvoice.builder()
                .monobankInvoiceId("inv-done")
                .amount(BigDecimal.valueOf(500))
                .status("success")
                .transactionId(transactionId)
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-done");
        payload.setStatus("success");
        payload.setAmount(50_000L);

        when(invoiceRepository.findByMonobankInvoiceId("inv-done")).thenReturn(Optional.of(invoice));

        monobankService.handleWebhook(payload);

        verify(transactionRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleWebhook_ignoresNullPayload() {
        monobankService.handleWebhook(null);

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreWebhookWhenInvoiceIdBlank() {
        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("   ");
        payload.setStatus("success");

        monobankService.handleWebhook(payload);

        verify(invoiceRepository, never()).findByMonobankInvoiceId(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldRecordServicePaymentWithoutUpdatingPrepayment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        MonobankInvoice invoice = MonobankInvoice.builder()
                .tenantId(tenantId)
                .appointmentId(appointmentId)
                .monobankInvoiceId("inv-service")
                .amount(BigDecimal.valueOf(1000))
                .status("pending")
                .paymentType("service_payment")
                .build();

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .prepayment(BigDecimal.ZERO)
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-service");
        payload.setStatus("success");
        payload.setFinalAmount(100_000L);

        when(invoiceRepository.findByMonobankInvoiceId("inv-service")).thenReturn(Optional.of(invoice));
        when(config.getToken()).thenReturn("REPLACE_TEST");
        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId)).thenReturn(Optional.of(appointment));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        monobankService.handleWebhook(payload);

        assertEquals("success", invoice.getStatus());
        assertNotNull(invoice.getTransactionId());
        assertEquals(BigDecimal.ZERO, appointment.getPrepayment());
        verify(appointmentRepository, never()).save(appointment);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void shouldActivateSubscriptionWhenWebhookSucceedsForSubscriptionInvoice() {
        UUID tenantId = UUID.randomUUID();
        MonobankInvoice invoice = MonobankInvoice.builder()
                .tenantId(tenantId)
                .monobankInvoiceId("inv-sub")
                .amount(BigDecimal.valueOf(299))
                .status("pending")
                .invoiceType("SUBSCRIPTION")
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-sub");
        payload.setStatus("success");
        payload.setAmount(29_900L);

        when(invoiceRepository.findByMonobankInvoiceId("inv-sub")).thenReturn(Optional.of(invoice));
        when(config.getToken()).thenReturn("REPLACE_TEST");

        monobankService.handleWebhook(payload);

        verify(subscriptionService).activateSubscription(tenantId, "inv-sub");
        verify(transactionRepository, never()).save(any());
        assertEquals("success", invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void shouldUpdateStatusWithoutTransactionWhenWebhookFails() {
        MonobankInvoice invoice = MonobankInvoice.builder()
                .monobankInvoiceId("inv-fail")
                .amount(BigDecimal.valueOf(500))
                .status("pending")
                .paymentType("deposit")
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-fail");
        payload.setStatus("failure");

        when(invoiceRepository.findByMonobankInvoiceId("inv-fail")).thenReturn(Optional.of(invoice));

        monobankService.handleWebhook(payload);

        assertEquals("failure", invoice.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void shouldSkipTransactionWhenAppointmentMissingOnSuccess() {
        UUID appointmentId = UUID.randomUUID();
        MonobankInvoice invoice = MonobankInvoice.builder()
                .appointmentId(appointmentId)
                .monobankInvoiceId("inv-orphan")
                .amount(BigDecimal.valueOf(500))
                .status("pending")
                .paymentType("deposit")
                .build();

        MonobankWebhookPayload payload = new MonobankWebhookPayload();
        payload.setInvoiceId("inv-orphan");
        payload.setStatus("success");
        payload.setAmount(50_000L);

        when(invoiceRepository.findByMonobankInvoiceId("inv-orphan")).thenReturn(Optional.of(invoice));
        when(config.getToken()).thenReturn("REPLACE_TEST");
        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId)).thenReturn(Optional.empty());

        monobankService.handleWebhook(payload);

        verify(transactionRepository, never()).save(any());
        assertEquals("success", invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void shouldRejectCreateInvoiceWhenAppointmentCancelled() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.OWNER);

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CANCELLED)
                .build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));

        CreateOnlineInvoiceRequest request = new CreateOnlineInvoiceRequest();
        request.setAppointmentId(appointmentId);
        request.setAmount(BigDecimal.valueOf(100));
        request.setPaymentType("deposit");

        assertThrows(BusinessRuleException.class, () -> monobankService.createInvoice(request));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void shouldCancelExistingPendingInvoiceWhenCreateInvoice() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.OWNER);

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        MonobankInvoice oldPending = MonobankInvoice.builder()
                .tenantId(tenantId)
                .appointmentId(appointmentId)
                .monobankInvoiceId("old-pending")
                .amount(BigDecimal.valueOf(100))
                .status("pending")
                .paymentType("deposit")
                .pageUrl("https://pay.monobank.ua/old")
                .build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(invoiceRepository.findByAppointmentIdAndStatus(appointmentId, "pending"))
                .thenReturn(Optional.of(oldPending));
        when(config.getToken()).thenReturn("REPLACE_TEST");
        when(config.getRedirectUrl()).thenReturn("http://localhost/redirect");
        when(config.getWebhookUrl()).thenReturn("http://localhost/webhook");
        when(config.getInvoiceValiditySeconds()).thenReturn(3600);
        when(invoiceRepository.save(any(MonobankInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOnlineInvoiceRequest request = new CreateOnlineInvoiceRequest();
        request.setAppointmentId(appointmentId);
        request.setAmount(BigDecimal.valueOf(250));
        request.setPaymentType("deposit");

        OnlineInvoiceDto result = monobankService.createInvoice(request);

        assertEquals("cancelled", oldPending.getStatus());
        assertEquals("pending", result.getStatus());
        assertEquals("deposit", result.getPaymentType());
        verify(invoiceRepository).save(oldPending);
    }
}
