package com.inkflow.crm.module.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    private UUID id;
    private String name;
    private String address;
    private String phone;
    private String googleMapsLink;
    private String color;
    private Boolean isActive;
    private String photoUrl;
    private String navigationInstructions;
    private String telegramContact;
    private String instagram;
    private String workingHoursStart;
    private String workingHoursEnd;
    private Integer staffCount;
    private Instant createdAt;
}
