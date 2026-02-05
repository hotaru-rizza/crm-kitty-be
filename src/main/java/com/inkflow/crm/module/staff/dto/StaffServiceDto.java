package com.inkflow.crm.module.staff.dto;

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
public class StaffServiceDto {
    private UUID id;
    private UUID serviceId;
    private String title;
    private String description;
    private String pricingType;
    private BigDecimal basePrice;
    private BigDecimal customPrice; // Staff-specific price (null if using base)
    private Integer baseDuration;
    private Integer customDuration; // Staff-specific duration (null if using base)
    private String color;
    private Boolean isActive;
}
