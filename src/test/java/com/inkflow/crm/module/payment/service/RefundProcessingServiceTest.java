package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.appointment.support.AppointmentAccessGuard;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.ProcessRefundRequest;
import com.inkflow.crm.module.payment.mapper.PaymentMapper;
import com.inkflow.crm.module.payment.support.ReceiptNumberGenerator;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundProcessingServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ReceiptNumberGenerator receiptNumberGenerator;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AppointmentAccessGuard appointmentAccessGuard;

    @InjectMocks
    private RefundProcessingService refundProcessingService;

    @BeforeEach
    void stubAccessGuard() {
        lenient().doNothing().when(appointmentAccessGuard).requireEdit(any());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processRefund_rejectsAmountAboveRefundable() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        Transaction original = Transaction.builder()
                .id(transactionId)
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(1000))
                .refundedAmount(BigDecimal.valueOf(200))
                .isRefunded(false)
                .build();

        when(transactionRepository.findByIdAndDeletedAtIsNull(transactionId))
                .thenReturn(Optional.of(original));

        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(transactionId)
                .amount(BigDecimal.valueOf(900))
                .reason("Too much")
                .build();

        assertThrows(BusinessRuleException.class, () -> refundProcessingService.processRefund(request));
    }

    @Test
    void processRefund_updatesOriginalTransactionRefundedAmount() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        Transaction original = Transaction.builder()
                .id(transactionId)
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .paymentType(PaymentType.SERVICE_PAYMENT)
                .amount(BigDecimal.valueOf(1000))
                .refundedAmount(BigDecimal.ZERO)
                .isRefunded(false)
                .build();

        when(transactionRepository.findByIdAndDeletedAtIsNull(transactionId))
                .thenReturn(Optional.of(original));
        when(receiptNumberGenerator.generate()).thenReturn("RCP-REF");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
        when(paymentMapper.toDto(any(Transaction.class))).thenReturn(PaymentDto.builder().build());

        BigDecimal refundAmount = BigDecimal.valueOf(300);
        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(transactionId)
                .amount(refundAmount)
                .paymentMethod("cash")
                .reason("Partial refund")
                .build();

        refundProcessingService.processRefund(request);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(transactionCaptor.capture());
        Transaction updatedOriginal = transactionCaptor.getAllValues().get(1);
        assertEquals(refundAmount, updatedOriginal.getRefundedAmount());
        assertFalse(updatedOriginal.getIsRefunded());
    }

    @Test
    void processRefund_reducesAppointmentPrepaymentForDeposit() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .prepayment(BigDecimal.valueOf(500))
                .build();

        Transaction original = Transaction.builder()
                .id(transactionId)
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .paymentType(PaymentType.DEPOSIT)
                .amount(BigDecimal.valueOf(500))
                .refundedAmount(BigDecimal.ZERO)
                .isRefunded(false)
                .appointment(appointment)
                .build();

        when(transactionRepository.findByIdAndDeletedAtIsNull(transactionId))
                .thenReturn(Optional.of(original));
        when(receiptNumberGenerator.generate()).thenReturn("RCP-REF");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
        when(paymentMapper.toDto(any(Transaction.class))).thenReturn(PaymentDto.builder().build());

        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(transactionId)
                .amount(BigDecimal.valueOf(200))
                .paymentMethod("card")
                .reason("Deposit return")
                .build();

        refundProcessingService.processRefund(request);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        assertEquals(BigDecimal.valueOf(300), appointmentCaptor.getValue().getPrepayment());
    }

    @Test
    void processRefund_rejectsAlreadyFullyRefundedTransaction() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        Transaction original = Transaction.builder()
                .id(transactionId)
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(1000))
                .refundedAmount(BigDecimal.valueOf(1000))
                .isRefunded(true)
                .build();

        when(transactionRepository.findByIdAndDeletedAtIsNull(transactionId))
                .thenReturn(Optional.of(original));

        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(transactionId)
                .amount(BigDecimal.TEN)
                .build();

        assertThrows(BusinessRuleException.class, () -> refundProcessingService.processRefund(request));
    }

    @Test
    void processRefund_rejectsMissingTransaction() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        when(transactionRepository.findByIdAndDeletedAtIsNull(transactionId))
                .thenReturn(Optional.empty());

        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(transactionId)
                .amount(BigDecimal.TEN)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> refundProcessingService.processRefund(request));
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
