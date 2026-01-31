package com.inkflow.crm.module.service.dto;

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
public class ServiceDto {
    private UUID id;
    private String title;
    private String description;
    private String pricingType;
    private BigDecimal price;
    private Integer duration;
    private String color;
    private Boolean isActive;
    private Instant createdAt;
}
