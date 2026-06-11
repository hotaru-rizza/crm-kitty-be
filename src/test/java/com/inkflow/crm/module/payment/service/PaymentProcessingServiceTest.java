package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.ProcessPaymentRequest;
import com.inkflow.crm.module.payment.mapper.PaymentMapper;
import com.inkflow.crm.module.payment.support.AppointmentPaymentSummaryCalculator;
import com.inkflow.crm.module.payment.support.ReceiptNumberGenerator;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private AppointmentPaymentSummaryCalculator summaryCalculator;

    @Mock
    private ReceiptNumberGenerator receiptNumberGenerator;

    @InjectMocks
    private PaymentProcessingService paymentProcessingService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processPayment_recordsCashPayment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(tenantId, userId);

        Appointment appointment = appointment(appointmentId, tenantId);
        Transaction saved = Transaction.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(500)).build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(userId, tenantId))
                .thenReturn(Optional.of(Staff.builder().id(userId).build()));
        when(summaryCalculator.calculate(appointment)).thenReturn(summary(appointmentId, BigDecimal.valueOf(1000)));
        when(receiptNumberGenerator.generate()).thenReturn("RCP-001");
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);
        when(paymentMapper.toDto(saved)).thenReturn(PaymentDto.builder().id(saved.getId()).amount(saved.getAmount()).build());

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.valueOf(500))
                .paymentMethod("cash")
                .build();

        PaymentDto result = paymentProcessingService.processPayment(request);

        assertEquals(BigDecimal.valueOf(500), result.getAmount());
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void processPayment_rejectsCancelledAppointment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Appointment appointment = appointment(appointmentId, tenantId);
        appointment.setStatus(AppointmentStatus.CANCELLED);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.TEN)
                .paymentMethod("cash")
                .build();

        assertThrows(BusinessRuleException.class, () -> paymentProcessingService.processPayment(request));
    }

    @Test
    void processPayment_rejectsInvalidSplitAmounts() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Appointment appointment = appointment(appointmentId, tenantId);

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(summaryCalculator.calculate(appointment)).thenReturn(summary(appointmentId, BigDecimal.valueOf(1000)));

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("split")
                .cashAmount(BigDecimal.valueOf(40))
                .cardAmount(BigDecimal.valueOf(40))
                .build();

        assertThrows(BusinessRuleException.class, () -> paymentProcessingService.processPayment(request));
    }

    @Test
    void processPayment_rejectsZeroAmount() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment(appointmentId, tenantId)));
        when(summaryCalculator.calculate(any())).thenReturn(summary(appointmentId, BigDecimal.valueOf(1000)));

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.ZERO)
                .paymentMethod("cash")
                .build();

        assertThrows(BusinessRuleException.class, () -> paymentProcessingService.processPayment(request));
    }

    @Test
    void processPayment_rejectsSplitWithoutCashAndCardAmounts() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment(appointmentId, tenantId)));
        when(summaryCalculator.calculate(any())).thenReturn(summary(appointmentId, BigDecimal.valueOf(1000)));

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("split")
                .build();

        assertThrows(BusinessRuleException.class, () -> paymentProcessingService.processPayment(request));
    }

    @Test
    void processPayment_recordsSplitPaymentWithCashAndCardBreakdown() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Appointment appointment = appointment(appointmentId, tenantId);
        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(summaryCalculator.calculate(appointment)).thenReturn(summary(appointmentId, BigDecimal.valueOf(1000)));
        when(receiptNumberGenerator.generate()).thenReturn("RCP-SPLIT");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toDto(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            return PaymentDto.builder().id(UUID.randomUUID()).amount(tx.getAmount()).build();
        });

        BigDecimal cash = BigDecimal.valueOf(60);
        BigDecimal card = BigDecimal.valueOf(40);
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("split")
                .cashAmount(cash)
                .cardAmount(card)
                .build();

        paymentProcessingService.processPayment(request);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction saved = transactionCaptor.getValue();
        assertEquals(cash, saved.getCashAmount());
        assertEquals(card, saved.getCardAmount());
        assertEquals(BigDecimal.valueOf(100), saved.getAmount());
    }

    @Test
    void processPayment_appliesDepositToAppointmentPrepayment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Appointment appointment = appointment(appointmentId, tenantId);
        appointment.setPrepayment(BigDecimal.valueOf(100));

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(summaryCalculator.calculate(appointment)).thenReturn(summary(appointmentId, BigDecimal.valueOf(900)));
        when(receiptNumberGenerator.generate()).thenReturn("RCP-DEP");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toDto(any(Transaction.class))).thenReturn(PaymentDto.builder().build());

        BigDecimal depositAmount = BigDecimal.valueOf(200);
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(depositAmount)
                .paymentMethod("card")
                .paymentType("deposit")
                .build();

        paymentProcessingService.processPayment(request);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        assertEquals(BigDecimal.valueOf(300), appointmentCaptor.getValue().getPrepayment());
    }

    @Test
    void processPayment_recordsTipAsSeparateTransaction() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Appointment appointment = appointment(appointmentId, tenantId);
        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(summaryCalculator.calculate(appointment)).thenReturn(summary(appointmentId, BigDecimal.valueOf(1000)));
        when(receiptNumberGenerator.generate()).thenReturn("RCP-001", "RCP-TIP");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toDto(any(Transaction.class))).thenReturn(PaymentDto.builder().build());

        BigDecimal tip = BigDecimal.valueOf(50);
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.valueOf(500))
                .paymentMethod("cash")
                .tipAmount(tip)
                .build();

        paymentProcessingService.processPayment(request);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(transactionCaptor.capture());
        List<Transaction> savedTransactions = transactionCaptor.getAllValues();
        assertEquals(PaymentType.SERVICE_PAYMENT, savedTransactions.get(0).getPaymentType());
        assertEquals(PaymentType.TIP, savedTransactions.get(1).getPaymentType());
        assertEquals(tip, savedTransactions.get(1).getAmount());
    }

    @Test
    void processPayment_rejectsMissingAppointment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.empty());

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(appointmentId)
                .amount(BigDecimal.TEN)
                .paymentMethod("cash")
                .build();

        assertThrows(ResourceNotFoundException.class, () -> paymentProcessingService.processPayment(request));
    }

    private Appointment appointment(UUID id, UUID tenantId) {
        return Appointment.builder()
                .id(id)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .price(BigDecimal.valueOf(1000))
                .artist(Staff.builder().id(UUID.randomUUID()).build())
                .location(Location.builder().id(UUID.randomUUID()).build())
                .service(Service.builder().id(UUID.randomUUID()).title("Tattoo").build())
                .build();
    }

    private AppointmentPaymentSummaryDto summary(UUID appointmentId, BigDecimal remaining) {
        return AppointmentPaymentSummaryDto.builder()
                .appointmentId(appointmentId)
                .remainingBalance(remaining)
                .totalPaid(BigDecimal.ZERO)
                .build();
    }

    private void authenticate(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
