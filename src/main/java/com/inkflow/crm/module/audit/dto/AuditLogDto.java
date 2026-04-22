package com.inkflow.crm.module.audit.dto;

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
public class AuditLogDto {
    private UUID id;
    private UUID actorId;
    private String actorName;
    private String action;
    private String entityType;
    private String entityId;
    private String entityLabel;
    private String details;
    private Instant createdAt;
}
