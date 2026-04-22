package com.inkflow.crm.module.giftcertificate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCertificateDto {
    private UUID id;
    private String code;
    private BigDecimal initialAmount;
    private BigDecimal remainingAmount;
    private String buyerName;
    private String buyerPhone;
    private String holderName;
    private String holderPhone;
    private String notes;
    private String status;
    private LocalDate expiresAt;
    private Instant usedAt;
    private Instant createdAt;
}
