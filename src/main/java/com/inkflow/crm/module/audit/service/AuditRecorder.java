package com.inkflow.crm.module.audit.service;

import com.inkflow.crm.common.http.HttpRequestUtils;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.event.AuditEvent;
import com.inkflow.crm.security.SecurityUtils;
import com.inkflow.crm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final ApplicationEventPublisher eventPublisher;
    private final InkflowProperties inkflowProperties;

    public void record(AuditAction action,
                       AuditEntityType entityType,
                       String entityId,
                       String entityLabel) {
        record(action, entityType, entityId, entityLabel, null, null);
    }

    public void record(AuditAction action,
                       AuditEntityType entityType,
                       String entityId,
                       String entityLabel,
                       UUID subjectClientId) {
        record(action, entityType, entityId, entityLabel, subjectClientId, null);
    }

    public void record(AuditAction action,
                       AuditEntityType entityType,
                       String entityId,
                       String entityLabel,
                       UUID subjectClientId,
                       String details) {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        if (principal == null) {
            return;
        }

        publish(new AuditEvent(
                principal.getTenantId(),
                principal.getId(),
                principal.getEmail(),
                action,
                entityType,
                entityId,
                entityLabel,
                subjectClientId,
                details,
                HttpRequestUtils.clientIpAddress()
        ));
    }

    public void recordSystem(UUID tenantId,
                           AuditAction action,
                           AuditEntityType entityType,
                           String entityId,
                           String entityLabel) {
        recordSystem(tenantId, action, entityType, entityId, entityLabel, null, null);
    }

    public void recordSystem(UUID tenantId,
                           AuditAction action,
                           AuditEntityType entityType,
                           String entityId,
                           String entityLabel,
                           UUID subjectClientId,
                           String details) {
        if (tenantId == null) {
            return;
        }

        publish(new AuditEvent(
                tenantId,
                null,
                inkflowProperties.getAudit().getSystemActorName(),
                action,
                entityType,
                entityId,
                entityLabel,
                subjectClientId,
                details,
                null
        ));
    }

    private void publish(AuditEvent event) {
        eventPublisher.publishEvent(event);
    }
}
