package com.inkflow.crm.module.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDto {
    private LocalDate date;
    private String dayOfWeek;
    private String dayName;
    private Boolean isAvailable; // Artist works this day
    private List<Slot> slots;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Slot {
        private LocalTime startTime;
        private LocalTime endTime;
        private String startTimeFormatted; // "10:00"
        private String endTimeFormatted;   // "12:00"
        private Boolean isAvailable;       // Not booked
    }
}
