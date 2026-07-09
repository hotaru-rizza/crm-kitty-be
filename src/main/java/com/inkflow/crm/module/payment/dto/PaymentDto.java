package com.inkflow.crm.module.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {
    private UUID id;
    private String paymentType;
    private String paymentTypeLabel;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentMethodLabel;
    private BigDecimal cashAmount;
    private BigDecimal cardAmount;
    private BigDecimal tipAmount;
    private String description;
    private Instant date;
    private String receiptNumber;


    private Boolean isRefunded;
    private BigDecimal refundedAmount;
    private BigDecimal refundableAmount;
    private Boolean canVoid;
    private UUID originalTransactionId;
    private String refundReason;


    private UUID processedById;
    private String processedByName;


    private String clientName;

    private Instant createdAt;
}
