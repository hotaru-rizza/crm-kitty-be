package com.inkflow.crm.module.payment.mapper;

import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentMapper {

    public PaymentDto toDto(Transaction transaction) {
        return PaymentDto.builder()
                .id(transaction.getId())
                .paymentType(transaction.getPaymentType() != null ? transaction.getPaymentType().getValue() : null)
                .paymentTypeLabel(transaction.getPaymentType() != null ? transaction.getPaymentType().getDescription() : null)
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod().getValue())
                .paymentMethodLabel(transaction.getPaymentMethod().getDescription())
                .cashAmount(transaction.getCashAmount())
                .cardAmount(transaction.getCardAmount())
                .tipAmount(transaction.getTipAmount())
                .description(transaction.getDescription())
                .date(transaction.getDate())
                .receiptNumber(transaction.getReceiptNumber())
                .isRefunded(transaction.getIsRefunded())
                .refundedAmount(transaction.getRefundedAmount())
                .refundableAmount(transaction.getRefundableAmount())
                .canVoid(resolveCanVoid(transaction))
                .originalTransactionId(transaction.getOriginalTransactionId())
                .refundReason(transaction.getRefundReason())
                .processedById(transaction.getProcessedBy() != null ? transaction.getProcessedBy().getId() : null)
                .processedByName(transaction.getProcessedBy() != null ? transaction.getProcessedBy().getFullName() : null)
                .clientName(resolveClientName(transaction))
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private String resolveClientName(Transaction transaction) {
        if (transaction.getAppointment() == null || transaction.getAppointment().getClient() == null) {
            return null;
        }
        var client = transaction.getAppointment().getClient();
        return client.getFirstName() + " " + client.getLastName();
    }

    private boolean resolveCanVoid(Transaction transaction) {
        if (transaction.isRefund() || transaction.getPaymentType() == PaymentType.TIP) {
            return false;
        }
        if (transaction.getPaymentType() != PaymentType.SERVICE_PAYMENT
                && transaction.getPaymentType() != PaymentType.DEPOSIT) {
            return false;
        }
        if (transaction.getRefundedAmount() != null
                && transaction.getRefundedAmount().compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        return transaction.getAppointment() != null && transaction.canBeRefunded();
    }
}
