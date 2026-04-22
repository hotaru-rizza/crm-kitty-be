package com.inkflow.crm.module.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAnalyticsDto {

    private int totalUniqueClients;
    private int newClients;
    private int returningClients;
    private double repeatRate;
    private List<DataPoint> series;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String date;
        private int newClients;
        private int returningClients;
        private int total;
    }
}
