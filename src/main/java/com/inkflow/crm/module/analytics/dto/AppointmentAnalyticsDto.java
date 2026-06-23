package com.inkflow.crm.module.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentAnalyticsDto {

    private List<DataPoint> series;
    private int totalAppointments;
    private int completedAppointments;
    private int cancelledAppointments;
    private int noShowAppointments;
    private int newAppointments;
    private BigDecimal totalRevenue;
    private BigDecimal avgCheck;
    private int newClients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String date;
        private int total;
        private int completed;
        private int cancelled;
        private int noShow;
        private BigDecimal revenue;
    }
}
