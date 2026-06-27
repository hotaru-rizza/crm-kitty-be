package com.inkflow.crm.module.audit.dto;

import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;

import java.util.UUID;

public record AuditContext(
        AuditAction attemptedAction,
        AuditEntityType entityType,
        String entityId,
        String entityLabel,
        UUID subjectClientId
) {
    public static AuditContext of(
            AuditAction attemptedAction,
            AuditEntityType entityType,
            String entityId,
            String entityLabel) {
        return new AuditContext(attemptedAction, entityType, entityId, entityLabel, null);
    }
}
