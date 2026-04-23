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
public class PnlDto {

    private BigDecimal revenue;
    private BigDecimal costOfSales;
    private BigDecimal grossProfit;
    private double grossMargin;

    private BigDecimal staffCommissions;
    private BigDecimal otherExpenses;
    private BigDecimal netProfit;
    private double netMargin;

    private List<CategoryLine> expenseBreakdown;
    private List<StaffLine> staffBreakdown;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryLine {
        private String categoryKey;
        private String label;
        private String color;
        private BigDecimal amount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StaffLine {
        private String name;
        private BigDecimal revenue;
        private BigDecimal commission;
        private String salaryType;
        private BigDecimal salaryRate;
    }
}
