package com.inkflow.crm.module.service.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceRequest {

    @Size(min = 2, max = 100, message = "Title must be between 2 and 100 characters")
    private String title;

    private String description;

    @Pattern(regexp = "^(fixed|hourly|project)$", message = "Pricing type must be fixed, hourly, or project")
    private String pricingType;

    @DecimalMin(value = "0.0", message = "Price must be positive")
    private BigDecimal price;

    @Min(value = 15, message = "Duration must be at least 15 minutes")
    private Integer duration;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code")
    private String color;

    private Boolean isActive;

    @DecimalMin(value = "0.0", message = "Cost price must be positive")
    private BigDecimal costPrice;
}
