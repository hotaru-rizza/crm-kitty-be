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
public class ServicePopularityDto {
    private UUID serviceId;
    private String name;
    private int totalAppointments;
    private int completedAppointments;
    private int cancelledAppointments;
    private BigDecimal revenue;
    private BigDecimal avgCheck;
    private BigDecimal completionRate;
    private BigDecimal costPrice;
}
