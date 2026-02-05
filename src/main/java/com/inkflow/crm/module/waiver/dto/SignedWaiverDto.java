package com.inkflow.crm.module.waiver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedWaiverDto {
    private UUID id;
    private UUID appointmentId;
    private UUID clientId;
    private String clientName;
    private UUID templateId;
    private String templateTitle;
    private String signatureData;
    private Map<String, Boolean> checkboxValues;
    private Instant signedAt;
}
