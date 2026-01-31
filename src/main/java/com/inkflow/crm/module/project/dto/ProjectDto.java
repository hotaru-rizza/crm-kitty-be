package com.inkflow.crm.module.project.dto;

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
    private Instant createdAt;
}
