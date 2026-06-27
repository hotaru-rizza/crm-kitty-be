package com.inkflow.crm.module.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLineRequest {

    @NotNull(message = "Line amount is required")
    @DecimalMin(value = "0.01", message = "Line amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Line payment method is required")
    private String paymentMethod;

    private String paymentType;

    private String category;

    private BigDecimal cashAmount;

    private BigDecimal cardAmount;
}
