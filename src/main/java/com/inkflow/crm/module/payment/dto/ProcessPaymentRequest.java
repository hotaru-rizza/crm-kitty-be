package com.inkflow.crm.module.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRequest {

    @NotNull(message = "Appointment ID is required")
    private UUID appointmentId;

    private BigDecimal amount;

    private String paymentMethod;

    private String paymentType;

    @Valid
    private List<PaymentLineRequest> lines;


    private BigDecimal cashAmount;
    private BigDecimal cardAmount;


    private BigDecimal tipAmount;


    private String description;
}
