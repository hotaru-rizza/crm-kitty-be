package com.inkflow.crm.module.giftcertificate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCertificateRequest {
    @NotNull
    @DecimalMin("1.0")
    private BigDecimal amount;
    private String buyerName;
    private String buyerPhone;
    private String holderName;
    private String holderPhone;
    private String notes;
    private LocalDate expiresAt;
}
