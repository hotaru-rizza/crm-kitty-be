package com.inkflow.crm.support;

import com.inkflow.crm.module.audit.support.AuditLabelFormatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

public final class AuditMocks {

    private AuditMocks() {
    }

    public static void stubLabelFormatter(AuditLabelFormatter auditLabelFormatter) {
        lenient().when(auditLabelFormatter.leave(any(), any(), any(), any())).thenReturn("Leave");
        lenient().when(auditLabelFormatter.staff(any())).thenReturn("Staff");
        lenient().when(auditLabelFormatter.catalogService(anyString())).thenReturn("Service");
        lenient().when(auditLabelFormatter.project(anyString())).thenReturn("Project");
        lenient().when(auditLabelFormatter.appointment(any(), any())).thenReturn("Appointment");
    }
}
