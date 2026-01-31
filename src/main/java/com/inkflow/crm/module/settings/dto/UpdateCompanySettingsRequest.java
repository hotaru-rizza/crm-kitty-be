package com.inkflow.crm.module.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanySettingsRequest {

    private Boolean smsReminders;
    private Boolean telegramReminders;
    private Boolean emailReminders;

    @Min(value = 1, message = "Reminder hours must be at least 1")
    @Max(value = 168, message = "Reminder hours must not exceed 168")
    private Integer reminderHoursBefore;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format (HH:mm)")
    private String workingHoursStart;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format (HH:mm)")
    private String workingHoursEnd;

    private Boolean allowOnlineBooking;

    @Min(value = 1, message = "Min advance hours must be at least 1")
    private Integer minAdvanceHours;

    @Min(value = 1, message = "Max advance days must be at least 1")
    @Max(value = 365, message = "Max advance days must not exceed 365")
    private Integer maxAdvanceDays;
}
