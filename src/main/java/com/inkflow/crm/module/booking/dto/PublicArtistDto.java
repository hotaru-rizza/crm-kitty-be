package com.inkflow.crm.module.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicArtistDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private List<String> specialization;
    private String instagram;
    private String calendarColor;


    private List<String> portfolioImages;


    private List<PublicServiceDto> services;


    private List<ScheduleDayDto> schedule;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDayDto {
        private String dayOfWeek;
        private String dayName;
        private Boolean isWorking;
        private String startTime;
        private String endTime;
    }
}
