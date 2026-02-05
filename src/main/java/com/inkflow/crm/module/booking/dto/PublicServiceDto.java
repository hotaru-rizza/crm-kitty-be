package com.inkflow.crm.module.booking.dto;

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
public class PublicServiceDto {
    private UUID id;
    private String title;
    private String description;
    private String pricingType; // fixed, hourly, from
    private BigDecimal price;
    private BigDecimal priceFrom; // for "from" pricing
    private BigDecimal priceTo;
    private Integer duration; // minutes
    private String color;
}
