package com.inkflow.crm.module.service.dto;

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
public class ServicePriceDto {
    private UUID serviceId;
    private UUID artistId;
    private BigDecimal price;
    private Integer duration;
    private Boolean isOverride;
}
