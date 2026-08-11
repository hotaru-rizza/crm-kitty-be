package com.inkflow.crm.module.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ProjectFilterRequest {
    private String search;
    private String status;
    private Boolean onlyMine;
    private List<UUID> artistId;
    private UUID clientId;

    private BigDecimal estimatedCostMin;
    private BigDecimal estimatedCostMax;
    private Integer paidPercentMin;
    private Integer paidPercentMax;
    private Integer totalSessionsMin;
    private Integer totalSessionsMax;
    private Integer completedSessionsMin;
    private Integer completedSessionsMax;
    private Instant createdAtFrom;
    private Instant createdAtTo;
    private Instant updatedAtFrom;
    private Instant updatedAtTo;
    private Boolean hasSketch;
    private Boolean hasPhotos;
}
