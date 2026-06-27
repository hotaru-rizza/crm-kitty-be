package com.inkflow.crm.module.appointment.dto;

import com.inkflow.crm.module.client.dto.ClientSummaryDto;
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
public class AppointmentDetailDto {
    private UUID id;
    private ClientSummaryDto client;
    private StaffSummaryDto artist;
    private AppointmentDto.ServiceSummaryDto service;
    private AppointmentDto.LocationSummaryDto location;
    private UUID projectId;
    private String projectTitle;
    private Instant startTime;
    private Instant endTime;
    private String status;
    private BigDecimal price;
    private BigDecimal prepayment;
    private BigDecimal discount;
    private BigDecimal finalPrice;
    private String notes;
    private String sketchImage;
    private String cancellationReason;
    private Instant cancelledAt;
    private List<AppointmentItemDto> items;
    private List<PhotoDto> photos;
    private boolean reservation;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoDto {
        private UUID id;
        private String url;
        private String stage;
    }
}
