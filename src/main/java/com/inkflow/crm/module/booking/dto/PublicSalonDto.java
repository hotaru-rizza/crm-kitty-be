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
public class PublicSalonDto {
    private UUID id;
    private String name;
    private String subdomain;
    private String description;
    private String logoUrl;
    private String coverUrl;
    private String phone;
    private String email;
    private String instagram;
    private String address;
    private String city;
    
    // Working hours
    private String workingHoursStart;
    private String workingHoursEnd;
    
    // Settings
    private Boolean allowOnlineBooking;
    private Integer minAdvanceHours;
    private Integer maxAdvanceDays;
    
    // Statistics (optional)
    private Integer artistsCount;
    private Integer servicesCount;
}
