package com.inkflow.crm.module.appointment.dto;

import com.inkflow.crm.module.client.dto.ClientSummaryDto;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
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
public class AppointmentDto {
    private UUID id;
    private ClientSummaryDto client;
    private StaffSummaryDto artist;
    private ServiceSummaryDto service;
    private LocationSummaryDto location;
    private UUID projectId;
    private Instant startTime;
    private Instant endTime;
    private String status;
    private BigDecimal price;
    private BigDecimal finalPrice;
    private Boolean waiverSigned;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceSummaryDto {
        private UUID id;
        private String title;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationSummaryDto {
        private UUID id;
        private String name;
        private String color;
    }
}
