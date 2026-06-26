package com.inkflow.crm.module.client.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class ClientFilterRequest {
    private String search;
    private Boolean dormant;
    private Boolean onlyMine;
    private Boolean lost;
    private Boolean blacklisted;
    private Boolean excludeBlacklisted;

    private Integer totalVisitsMin;
    private Integer totalVisitsMax;
    private Integer cancelledVisitsMin;
    private Integer cancelledVisitsMax;
    private BigDecimal balanceMin;
    private BigDecimal balanceMax;
    private BigDecimal ltvMin;
    private BigDecimal ltvMax;
    private BigDecimal avgCheckMin;
    private BigDecimal avgCheckMax;

    private Instant createdAtFrom;
    private Instant createdAtTo;
    private Instant lastVisitFrom;
    private Instant lastVisitTo;
    private Instant firstVisitFrom;
    private Instant firstVisitTo;
    private Instant activeAppointmentFrom;
    private Instant activeAppointmentTo;
    private LocalDate birthdayFrom;
    private LocalDate birthdayTo;

    private UUID artistId;
    private List<UUID> serviceIds;
    private List<String> tags;
    private Boolean hasActiveAppointments;
}
