package com.inkflow.crm.module.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDetailDto {
    private UUID id;
    private String title;
    private String description;
    private String pricingType;
    private BigDecimal price;
    private Integer duration;
    private String color;
    private Boolean isActive;
    private List<ArtistPricingOverrideDto> artistPricing;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtistPricingOverrideDto {
        private UUID artistId;
        private String artistName;
        private BigDecimal price;
        private Integer duration;
    }
}
