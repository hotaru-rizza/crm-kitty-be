package com.inkflow.crm.module.notification.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.notification.event.AppointmentReminderEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderSchedulerTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AppointmentReminderScheduler scheduler;

    @Test
    void shouldPublishReminderEventForUpcomingAppointment() {
        UUID appointmentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-11T14:00:00Z");
        Appointment appointment = buildAppointment(appointmentId, tenantId, artistId, clientId, "Anna", "Client", startTime);

        when(appointmentRepository.findUpcomingForReminders(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));

        scheduler.checkUpcomingAppointments();

        ArgumentCaptor<AppointmentReminderEvent> eventCaptor = ArgumentCaptor.forClass(AppointmentReminderEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AppointmentReminderEvent event = eventCaptor.getValue();
        assertEquals(appointmentId, event.appointmentId());
        assertEquals(tenantId, event.tenantId());
        assertEquals(artistId, event.staffId());
        assertEquals(clientId, event.clientId());
        assertEquals("Anna Client", event.clientName());
        assertEquals(startTime, event.startTime());
    }

    @Test
    void shouldNotPublishDuplicateReminderForSameAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = buildAppointment(
                appointmentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Anna", "Client", Instant.parse("2026-06-11T14:00:00Z"));

        when(appointmentRepository.findUpcomingForReminders(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));

        scheduler.checkUpcomingAppointments();
        scheduler.checkUpcomingAppointments();

        verify(eventPublisher, times(1)).publishEvent(any(AppointmentReminderEvent.class));
    }

    @Test
    void shouldRepublishReminderAfterAppointmentLeavesWindow() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = buildAppointment(
                appointmentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Anna", "Client", Instant.parse("2026-06-11T14:00:00Z"));

        when(appointmentRepository.findUpcomingForReminders(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment), List.of(), List.of(appointment));

        scheduler.checkUpcomingAppointments();
        scheduler.checkUpcomingAppointments();
        scheduler.checkUpcomingAppointments();

        verify(eventPublisher, times(2)).publishEvent(any(AppointmentReminderEvent.class));
    }

    private Appointment buildAppointment(
            UUID appointmentId,
            UUID tenantId,
            UUID artistId,
            UUID clientId,
            String firstName,
            String lastName,
            Instant startTime) {
        return Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .artist(Staff.builder().id(artistId).build())
                .client(Client.builder().id(clientId).firstName(firstName).lastName(lastName).build())
                .startTime(startTime)
                .build();
    }
}
