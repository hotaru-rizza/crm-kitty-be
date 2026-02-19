package com.inkflow.crm.module.monobank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateOnlineInvoiceRequest {

    @NotNull
    private UUID appointmentId;

    @NotNull
    @DecimalMin(value = "1.0", message = "Amount must be at least 1 UAH")
    private BigDecimal amount;

    @Pattern(regexp = "^(service_payment|deposit)$", message = "Invalid payment type")
    private String paymentType = "service_payment";
}
