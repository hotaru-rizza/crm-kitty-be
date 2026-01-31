package com.inkflow.crm.module.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySettingsDto {
    private Boolean smsReminders;
    private Boolean telegramReminders;
    private Boolean emailReminders;
    private Integer reminderHoursBefore;
    private String workingHoursStart;
    private String workingHoursEnd;
    private Boolean allowOnlineBooking;
    private Integer minAdvanceHours;
    private Integer maxAdvanceDays;
    private Instant updatedAt;
}
