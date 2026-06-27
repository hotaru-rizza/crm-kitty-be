package com.inkflow.crm.module.audit.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRecorderSystemTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private AuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        InkflowProperties.Audit audit = new InkflowProperties.Audit();
        audit.setSystemActorName("system@inkflow");
        when(inkflowProperties.getAudit()).thenReturn(audit);
    }

    @Test
    void recordSystem_publishesEventWithSystemActor() {
        UUID tenantId = UUID.randomUUID();

        auditRecorder.recordSystem(
                tenantId,
                AuditAction.PAYMENT,
                AuditEntityType.APPOINTMENT,
                "appt-1",
                "Запис · Test",
                null,
                "Monobank"
        );

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        AuditEvent event = captor.getValue();
        assertEquals(tenantId, event.tenantId());
        assertNull(event.actorId());
        assertEquals("system@inkflow", event.actorName());
        assertEquals(AuditAction.PAYMENT, event.action());
        assertEquals("Monobank", event.details());
    }
}
