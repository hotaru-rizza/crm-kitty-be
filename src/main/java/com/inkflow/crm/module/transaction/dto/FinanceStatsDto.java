package com.inkflow.crm.module.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceStatsDto {
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal avgCheck;
    private Long incomeCount;
    private Map<String, BigDecimal> byCategory;
    private Map<String, BigDecimal> byPaymentMethod;
    private List<ArtistRevenueDto> byArtist;
    private Map<String, BigDecimal> byDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtistRevenueDto {
        private String artistId;
        private String artistName;
        private BigDecimal revenue;
        private Integer appointmentsCount;
        private String calendarColor;
        private String avatar;
    }
}
