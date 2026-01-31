package com.inkflow.crm.module.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScheduleRequest {

    @NotEmpty(message = "Schedule is required")
    @Valid
    private List<ScheduleEntry> schedule;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleEntry {

        @NotNull(message = "Day of week is required")
        @Pattern(regexp = "^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)$", message = "Invalid day of week")
        private String dayOfWeek;

        @NotNull(message = "isWorking flag is required")
        private Boolean isWorking;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format (HH:mm)")
        private String startTime;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Invalid time format (HH:mm)")
        private String endTime;
    }
}
