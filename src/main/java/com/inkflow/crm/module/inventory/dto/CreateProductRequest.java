package com.inkflow.crm.module.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    @NotBlank
    private String name;
    private String category;
    private String sku;
    private String description;
    @NotNull @NotBlank
    private String unit;
    private BigDecimal costPerUnit;
    private Double initialStock;
    private Double minStockLevel;
}
