package com.inkflow.crm.module.onboarding.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class OnboardingResponse {
    private UUID userId;
    private UUID tenantId;
    private String tenantName;
    private String role;
    private boolean success;
}
