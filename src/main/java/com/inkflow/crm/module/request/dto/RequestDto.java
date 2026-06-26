package com.inkflow.crm.module.request.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestDto {
    private UUID id;
    private String source;
    private String clientName;
    private String clientNickname;
    private String message;
    private String phone;
    private String email;
    private String instagram;
    private String status;
    private UUID convertedClientId;
    private UUID matchedClientId;
    private String matchedClientName;
    private Boolean matchedClientBlacklisted;
    private Instant createdAt;
    private Instant repliedAt;
    private Instant convertedAt;
    private String sketchUrl;
    private String idea;
    private String city;
    private String tattooSize;
    private String tattooTiming;
    private Boolean isCoverUp;
    private List<String> bodyZones;
    private List<String> referenceUrls;
    private String contactMethod;
    private String contactValue;
    private UUID assignedStaffId;
    private String assignedStaffName;
}
