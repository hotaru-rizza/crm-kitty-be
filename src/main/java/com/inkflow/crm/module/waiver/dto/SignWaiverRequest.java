package com.inkflow.crm.module.waiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignWaiverRequest {
    
    @NotNull(message = "Appointment ID is required")
    private UUID appointmentId;
    
    @NotNull(message = "Template ID is required")
    private UUID templateId;
    
    @NotBlank(message = "Signature data is required")
    private String signatureData; // Base64 encoded signature image
    
    private Map<String, Boolean> checkboxValues; // checkbox id -> checked state
}
