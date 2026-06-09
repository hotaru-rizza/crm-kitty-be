package com.inkflow.crm.module.staff.dto;

import com.inkflow.crm.module.location.dto.LocationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDetailDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String avatar;
    private String role;
    private String calendarColor;
    private List<String> specialization;
    private List<String> portfolioImages;
    private String bio;
    private String status;
    private String accountStatus;
    private List<LocationDto> locations;
    private List<ScheduleDto> schedule;
    private StaffStatsDto stats;
    private String salaryType;
    private BigDecimal salaryRate;
    private String bankDetails;
    private Boolean isServiceProvider;
    private String instagram;
    private BigDecimal hourlyRate;
    private List<String> dontDoList;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDto {
        private String dayOfWeek;
        private Boolean isWorking;
        private String startTime;
        private String endTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffStatsDto {
        private Integer appointmentsThisMonth;
        private Integer upcomingAppointments;
    }
}
