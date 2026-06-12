package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private CompanySettingsRepository companySettingsRepository;
    @Mock private AppointmentNotificationService appointmentNotificationService;

    @InjectMocks
    private ReminderScheduler reminderScheduler;

    @Test
    void processReminders_skipsWhenEmailRemindersDisabled() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(false)
                .emailAftercare(false)
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));

        reminderScheduler.processReminders();

        verify(appointmentRepository, never()).findByTenantIdAndDateRange(any(), any(), any());
        verify(appointmentNotificationService, never()).sendReminder(any(), anyInt());
        verify(appointmentNotificationService, never()).sendAftercare(any());
    }

    @Test
    void processReminders_sendsReminderForEligibleAppointments() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();

        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().firstName("Anna").email("anna@test.com").build())
                .startTime(Instant.now().plusSeconds(86400))
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(), any()))
                .thenReturn(List.of(appointment));

        reminderScheduler.processReminders();

        verify(appointmentNotificationService).sendReminder(appointment, 24);
    }

    @Test
    void processReminders_skipsIneligibleStatuses() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();

        Appointment cancelledAppointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.CANCELLED)
                .client(Client.builder().firstName("Anna").email("anna@test.com").build())
                .startTime(Instant.now().plusSeconds(86400))
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of(cancelledAppointment));

        reminderScheduler.processReminders();

        verify(appointmentNotificationService, never()).sendReminder(any(), anyInt());
    }

    @Test
    void processReminders_sendsAftercareForDoneAppointments() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(false)
                .emailAftercare(true)
                .reminderHoursBefore(24)
                .build();

        Appointment doneAppointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.DONE)
                .client(Client.builder().firstName("Bob").email("bob@test.com").build())
                .startTime(Instant.now().minusSeconds(86400))
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of(doneAppointment));

        reminderScheduler.processReminders();

        verify(appointmentNotificationService).sendAftercare(doneAppointment);
    }

    @Test
    void processReminders_continuesAfterErrorForOneTenant() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        // Should not throw
        reminderScheduler.processReminders();
    }
}
