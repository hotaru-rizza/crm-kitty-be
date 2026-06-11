package com.inkflow.crm.module.project.dto;

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
public class ProjectDto {
    private UUID id;
    private String title;
    private String description;
    private ClientSummaryDto client;
    private StaffSummaryDto artist;
    private String status;
    private BigDecimal estimatedCost;
    private BigDecimal totalPaid;
    private Integer totalSessions;
    private Integer completedSessions;
    private String sketchImage;
    private Instant createdAt;
    private List<PhotoDto> photos;
    private List<SessionDto> sessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoDto {
        private UUID id;
        private String url;
        private String stage;
        private Instant uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionDto {
        private UUID id;
        private Instant startTime;
        private Instant endTime;
        private String status;
        private UUID serviceId;
        private String serviceName;
        private String serviceColor;
        private BigDecimal price;
        private BigDecimal finalPrice;
        private String notes;
        private Integer photosCount;
    }
}
