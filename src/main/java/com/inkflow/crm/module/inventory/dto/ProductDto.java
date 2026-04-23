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
public class ProductDto {
    private UUID id;
    private String name;
    private String category;
    private String sku;
    private String description;
    private String unit;
    private BigDecimal costPerUnit;
    private Double stockQuantity;
    private Double minStockLevel;
    private Boolean isActive;
    private boolean lowStock;
    private Instant createdAt;
}
