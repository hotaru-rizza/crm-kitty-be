package com.inkflow.crm.module.location.dto;

import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
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
public class LocationDetailDto {
    private UUID id;
    private String name;
    private String address;
    private String phone;
    private String googleMapsLink;
    private String color;
    private Boolean isActive;
    private String photoUrl;
    private List<StaffSummaryDto> staff;
    private LocationStatsDto stats;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationStatsDto {
        private Integer appointmentsThisMonth;
        private BigDecimal revenueThisMonth;
    }
}
