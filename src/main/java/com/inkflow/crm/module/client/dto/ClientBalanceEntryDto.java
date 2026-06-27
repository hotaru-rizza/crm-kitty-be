package com.inkflow.crm.module.client.dto;

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
public class ClientBalanceEntryDto {
    private UUID id;
    private BigDecimal amount;
    private String reason;
    private UUID appointmentId;
    private UUID transactionId;
    private String note;
    private Instant createdAt;
}
