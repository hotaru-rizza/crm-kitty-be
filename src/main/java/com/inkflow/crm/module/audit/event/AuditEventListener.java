package com.inkflow.crm.module.audit.event;

import com.inkflow.crm.module.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogService auditLogService;

    @Async
    @EventListener
    public void onAudit(AuditEvent event) {
        try {
            auditLogService.log(
                    event.tenantId(),
                    event.actorId(),
                    event.actorName(),
                    event.action(),
                    event.entityType(),
                    event.entityId(),
                    event.entityLabel(),
                    event.subjectClientId(),
                    event.details(),
                    event.ipAddress()
            );
        } catch (Exception exception) {
            log.warn("Failed to handle audit event action={} entityId={}: {}",
                    event.action(), event.entityId(), exception.getMessage());
        }
    }
}
