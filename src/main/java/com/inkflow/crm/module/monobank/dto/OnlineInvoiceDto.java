package com.inkflow.crm.module.monobank.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OnlineInvoiceDto {
    private UUID id;
    private UUID appointmentId;
    private String monobankInvoiceId;
    private BigDecimal amount;
    private String pageUrl;
    private String status;
    private String paymentType;
    private Instant expiresAt;
    private Instant createdAt;
}
