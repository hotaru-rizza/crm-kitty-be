package com.inkflow.crm.module.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessRefundRequest {

    @NotNull(message = "Original transaction ID is required")
    private UUID transactionId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Refund reason is required")
    private String reason;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}
