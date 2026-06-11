package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.EmailType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReminderScheduler reminderScheduler;

    @Test
    void shouldSkipReminderProcessingWhenEmailRemindersDisabled() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(false)
                .emailAftercare(false)
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));

        reminderScheduler.processReminders();

        verify(appointmentRepository, never()).findByTenantIdAndDateRange(any(), any(), any());
        verify(emailService, never()).sendReminder(any(), any(Integer.class));
        verify(emailService, never()).sendAftercare(any());
    }

    @Test
    void shouldSendReminderForEligibleConfirmedAppointment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().email("client@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));
        when(emailService.wasAlreadySent(appointmentId, EmailType.REMINDER)).thenReturn(false);

        reminderScheduler.processReminders();

        verify(emailService).sendReminder(appointment, 24);
        verify(emailService, never()).sendAftercare(any());
    }

    @Test
    void shouldSkipReminderWhenAppointmentNotEligible() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CANCELLED)
                .client(Client.builder().email("client@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));

        reminderScheduler.processReminders();

        verify(emailService, never()).sendReminder(any(), any(Integer.class));
    }

    @Test
    void shouldSendAftercareForDoneAppointmentInWindow() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(false)
                .emailAftercare(true)
                .build();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.DONE)
                .client(Client.builder().email("client@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));
        when(emailService.wasAlreadySent(appointmentId, EmailType.AFTERCARE)).thenReturn(false);

        reminderScheduler.processReminders();

        verify(emailService).sendAftercare(appointment);
        verify(emailService, never()).sendReminder(any(), any(Integer.class));
    }
}
