package com.inkflow.crm.module.inventory.dto;

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
public class StockOperationDto {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productUnit;
    private String type;
    private Double quantity;
    private BigDecimal costPerUnit;
    private String note;
    private String staffName;
    private Instant createdAt;
}
