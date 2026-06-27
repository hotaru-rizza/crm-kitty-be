package com.inkflow.crm.module.audit.event;

import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;

import java.util.UUID;

public record AuditEvent(
        UUID tenantId,
        UUID actorId,
        String actorName,
        AuditAction action,
        AuditEntityType entityType,
        String entityId,
        String entityLabel,
        UUID subjectClientId,
        String details,
        String ipAddress
) {
}
