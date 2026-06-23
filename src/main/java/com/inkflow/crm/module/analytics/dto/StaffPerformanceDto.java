package com.inkflow.crm.module.analytics.dto;

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
public class StaffPerformanceDto {
    private UUID staffId;
    private String name;
    private String avatar;
    private String calendarColor;
    private int totalAppointments;
    private int completedAppointments;
    private int cancelledAppointments;
    private int noShowAppointments;
    private BigDecimal revenue;
    private BigDecimal avgCheck;
    private String salaryType;
    private BigDecimal salaryRate;
    private BigDecimal calculatedSalary;
    private double scheduledHours;
    private double bookedHours;
    private double utilizationRate;
}
