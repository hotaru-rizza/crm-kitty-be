package com.inkflow.crm.module.staff.dto;

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
public class StaffEarningsDto {

    private UUID staffId;
    private BigDecimal revenue;
    private String salaryType;
    private BigDecimal rate;
    private BigDecimal earnings;
    private int appointmentsCount;
}
