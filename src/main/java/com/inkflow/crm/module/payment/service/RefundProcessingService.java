package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.ProcessRefundRequest;
import com.inkflow.crm.module.payment.mapper.PaymentMapper;
import com.inkflow.crm.module.payment.support.ReceiptNumberGenerator;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundProcessingService {

    private final TransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final PaymentMapper paymentMapper;
    private final ReceiptNumberGenerator receiptNumberGenerator;

    @Transactional
    public PaymentDto processRefund(ProcessRefundRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Transaction originalTransaction = transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                request.getTransactionId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(request.getTransactionId().toString()));

        validateRefundable(originalTransaction, request.getAmount());

        Staff processedBy = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(currentUserId, tenantId)
                .orElse(null);

        Transaction refundTransaction = buildRefundTransaction(request, originalTransaction, processedBy, tenantId);
        refundTransaction = transactionRepository.save(refundTransaction);

        originalTransaction.addRefundedAmount(request.getAmount());
        transactionRepository.save(originalTransaction);

        log.info("Refund processed: tenantId={} refundId={} originalTransactionId={}",
                tenantId, refundTransaction.getId(), originalTransaction.getId());

        adjustDepositAfterRefund(originalTransaction, request.getAmount());

        return paymentMapper.toDto(refundTransaction);
    }

    private void validateRefundable(Transaction originalTransaction, BigDecimal refundAmount) {
        if (!originalTransaction.canBeRefunded()) {
            throw new BusinessRuleException("This transaction cannot be refunded");
        }

        if (refundAmount.compareTo(originalTransaction.getRefundableAmount()) > 0) {
            throw new BusinessRuleException(
                    String.format("Refund amount (%.2f) exceeds refundable amount (%.2f)",
                            refundAmount, originalTransaction.getRefundableAmount()));
        }
    }

    private Transaction buildRefundTransaction(
            ProcessRefundRequest request,
            Transaction originalTransaction,
            Staff processedBy,
            UUID tenantId) {
        return Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.EXPENSE)
                .category(TransactionCategory.SERVICE)
                .paymentType(PaymentType.REFUND)
                .amount(request.getAmount())
                .paymentMethod(PaymentMethod.fromValue(request.getPaymentMethod()))
                .description(String.format("Повернення: %s", request.getReason()))
                .appointment(originalTransaction.getAppointment())
                .staff(originalTransaction.getStaff())
                .processedBy(processedBy)
                .location(originalTransaction.getLocation())
                .date(Instant.now())
                .originalTransactionId(originalTransaction.getId())
                .refundReason(request.getReason())
                .receiptNumber(receiptNumberGenerator.generate())
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();
    }

    private void adjustDepositAfterRefund(Transaction originalTransaction, BigDecimal refundAmount) {
        if (originalTransaction.getPaymentType() != PaymentType.DEPOSIT) {
            return;
        }

        Appointment appointment = originalTransaction.getAppointment();
        if (appointment == null) {
            return;
        }

        BigDecimal newPrepayment = appointment.getPrepayment().subtract(refundAmount);
        appointment.setPrepayment(newPrepayment.max(BigDecimal.ZERO));
        appointmentRepository.save(appointment);
    }
}
