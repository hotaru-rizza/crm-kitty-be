package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.ProcessPaymentRequest;
import com.inkflow.crm.module.payment.dto.ProcessRefundRequest;
import com.inkflow.crm.module.payment.support.AppointmentPaymentSummaryCalculator;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentProcessingService paymentProcessingService;

    @Mock
    private RefundProcessingService refundProcessingService;

    @Mock
    private AppointmentPaymentSummaryCalculator summaryCalculator;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAppointmentPaymentSummary_delegatesToCalculator() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        Appointment appointment = appointment(appointmentId, tenantId);
        AppointmentPaymentSummaryDto summary = AppointmentPaymentSummaryDto.builder()
                .appointmentId(appointmentId)
                .remainingBalance(BigDecimal.valueOf(500))
                .build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(summaryCalculator.calculate(appointment)).thenReturn(summary);

        AppointmentPaymentSummaryDto result = paymentService.getAppointmentPaymentSummary(appointmentId);

        assertEquals(BigDecimal.valueOf(500), result.getRemainingBalance());
        verify(summaryCalculator).calculate(appointment);
    }

    @Test
    void getAppointmentPayments_returnsPaymentList() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment(appointmentId, tenantId)));
        when(summaryCalculator.listPayments(appointmentId)).thenReturn(List.of(
                PaymentDto.builder().id(UUID.randomUUID()).amount(BigDecimal.TEN).build()
        ));

        List<PaymentDto> payments = paymentService.getAppointmentPayments(appointmentId);

        assertEquals(1, payments.size());
        verify(summaryCalculator).listPayments(appointmentId);
    }

    @Test
    void processPayment_delegatesToPaymentProcessingService() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(250))
                .paymentMethod("cash")
                .build();
        PaymentDto expected = PaymentDto.builder().id(UUID.randomUUID()).amount(request.getAmount()).build();

        when(paymentProcessingService.processPayment(request)).thenReturn(expected);

        PaymentDto result = paymentService.processPayment(request);

        assertEquals(expected, result);
        verify(paymentProcessingService).processPayment(request);
    }

    @Test
    void processRefund_delegatesToRefundProcessingService() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100))
                .reason("Client request")
                .paymentMethod("cash")
                .build();
        PaymentDto expected = PaymentDto.builder().id(UUID.randomUUID()).amount(request.getAmount()).build();

        when(refundProcessingService.processRefund(request)).thenReturn(expected);

        PaymentDto result = paymentService.processRefund(request);

        assertEquals(expected, result);
        verify(refundProcessingService).processRefund(request);
    }

    @Test
    void getAppointmentPayments_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.getAppointmentPayments(appointmentId));
    }

    @Test
    void getAppointmentPaymentSummary_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.getAppointmentPaymentSummary(appointmentId));
    }

    private Appointment appointment(UUID id, UUID tenantId) {
        return Appointment.builder()
                .id(id)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .price(BigDecimal.valueOf(1000))
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .build();
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
