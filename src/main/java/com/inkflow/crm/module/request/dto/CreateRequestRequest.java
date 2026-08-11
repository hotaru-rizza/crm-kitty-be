package com.inkflow.crm.module.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class CreateRequestRequest {

    @NotBlank(message = "Source is required")
    @Pattern(regexp = "^(instagram|telegram|website|referral|walk_in|other)$", message = "Invalid source")
    private String source;

    @Size(max = 100, message = "Client name must not exceed 100 characters")
    private String clientName;

    @Size(max = 50, message = "Client nickname must not exceed 50 characters")
    private String clientNickname;

    private String message;
    private String phone;
    private String email;
    private String instagram;
    private String sketchUrl;

    private UUID assignedStaffId;
    private UUID clientId;

    @Size(max = 30)
    private String tattooTiming;

    @Size(max = 30)
    private String tattooSize;

    private List<String> bodyZones;
    private Boolean isCoverUp;

    @Size(max = 5000)
    private String idea;

    private List<String> references;

    @Size(max = 50)
    private String city;

    @Pattern(regexp = "^(telegram|instagram|phone|email)?$", message = "Invalid contact method")
    private String contactMethod;

    @Size(max = 255)
    private String contactValue;
}
