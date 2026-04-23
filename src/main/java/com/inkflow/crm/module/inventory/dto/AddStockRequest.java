package com.inkflow.crm.module.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AddStockRequest {
    @NotNull
    private UUID productId;
    @NotNull @Positive
    private Double quantity;
    private BigDecimal costPerUnit;
    private String note;
    private String type; // ARRIVAL | WRITEOFF | ADJUSTMENT
}
