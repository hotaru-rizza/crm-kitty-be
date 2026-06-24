package com.inkflow.crm.module.transaction.dto;

import jakarta.validation.constraints.*;
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
public class CreateTransactionRequest {

    @NotBlank(message = "Type is required")
    @Pattern(regexp = "^(income|expense)$", message = "Type must be income or expense")
    private String type;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(cash|card|split)$", message = "Invalid payment method")
    private String paymentMethod;

    private String description;

    private UUID appointmentId;
    private UUID staffId;

    @NotNull(message = "Location ID is required")
    private UUID locationId;

    @NotNull(message = "Date is required")
    private Instant date;

    private BigDecimal cashAmount;
    private BigDecimal cardAmount;
    private BigDecimal tipAmount;
}
