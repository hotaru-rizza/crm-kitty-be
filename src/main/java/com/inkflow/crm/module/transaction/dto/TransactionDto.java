package com.inkflow.crm.module.transaction.dto;

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
public class TransactionDto {
    private UUID id;
    private String type;
    private String category;
    private BigDecimal amount;
    private String paymentMethod;
    private String description;
    private UUID appointmentId;
    private UUID staffId;
    private String staffName;
    private String staffAccountStatus;
    private boolean staffDeleted;
    private UUID locationId;
    private String locationName;
    private Instant date;
    private BigDecimal cashAmount;
    private BigDecimal cardAmount;
    private BigDecimal tipAmount;
    private Instant createdAt;
}
