package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.module.appointment.dto.AppointmentUpdateContext;
import com.inkflow.crm.module.appointment.event.AppointmentCanceledEvent;
import com.inkflow.crm.module.appointment.event.AppointmentCompletedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentConfirmedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentRescheduledEvent;
import com.inkflow.crm.module.audit.service.AuditLogService;
import com.inkflow.crm.module.email.service.sending.AppointmentNotificationService;
import com.inkflow.crm.module.google.service.GoogleCalendarSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentSideEffectServiceTest {

    @Mock
    private AppointmentNotificationService appointmentNotificationService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private GoogleCalendarSyncService googleCalendarSyncService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AppointmentSideEffectService sideEffectService;

    @Test
    void afterCreate_publishesConfirmedEventAndSyncsCalendar() {
        Appointment appointment = appointment(UUID.randomUUID());

        sideEffectService.afterCreate(appointment);

        verify(eventPublisher).publishEvent(any(AppointmentConfirmedEvent.class));
        verify(googleCalendarSyncService).syncNewAppointment(appointment);
        verify(auditLogService).logCurrent("CREATE", "APPOINTMENT", appointment.getId().toString(),
                "John Doe @ " + appointment.getStartTime());
    }

    @Test
    void afterUpdate_onDone_publishesCompletedEvent() {
        Appointment appointment = appointment(UUID.randomUUID());
        appointment.setStatus(AppointmentStatus.COMPLETED);

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.SCHEDULED, "completed", false));

        verify(eventPublisher).publishEvent(any(AppointmentCompletedEvent.class));
        verify(googleCalendarSyncService).syncUpdatedAppointment(appointment);
    }

    @Test
    void afterUpdate_onCancelled_publishesCanceledEvent() {
        Appointment appointment = appointment(UUID.randomUUID());
        appointment.setStatus(AppointmentStatus.CANCELLED);

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.SCHEDULED, "cancelled", false));

        verify(eventPublisher).publishEvent(any(AppointmentCanceledEvent.class));
        verify(googleCalendarSyncService).syncDeletedAppointment(appointment);
    }

    @Test
    void afterUpdate_onReschedule_publishesRescheduledEvent() {
        Appointment appointment = appointment(UUID.randomUUID());

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.SCHEDULED, null, true));

        verify(eventPublisher).publishEvent(any(AppointmentRescheduledEvent.class));
        verify(appointmentNotificationService, never()).sendConfirmation(appointment);
    }

    private Appointment appointment(UUID tenantId) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.SCHEDULED)
                .client(Client.builder().firstName("John").lastName("Doe").email("john@test.com").build())
                .startTime(Instant.parse("2026-06-14T10:00:00Z"))
                .build();
    }
}
