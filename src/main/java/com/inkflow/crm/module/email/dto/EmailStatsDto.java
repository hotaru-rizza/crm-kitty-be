package com.inkflow.crm.module.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatsDto {
    private long totalToday;
    private long totalWeek;
    private long totalMonth;
    private long confirmationsMonth;
    private long remindersMonth;
    private long aftercareMonth;
    private long manualMonth;
}
