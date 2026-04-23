package com.inkflow.crm.module.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {
    private String name;
    private String category;
    private String sku;
    private String description;
    private String unit;
    private BigDecimal costPerUnit;
    private Double minStockLevel;
    private Boolean isActive;
}
