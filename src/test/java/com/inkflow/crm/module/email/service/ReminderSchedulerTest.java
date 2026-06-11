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
import static org.mockito.Mockito.doThrow;
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

    @Test
    void shouldSkipReminderWhenAlreadySent() {
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
        when(emailService.wasAlreadySent(appointmentId, EmailType.REMINDER)).thenReturn(true);

        reminderScheduler.processReminders();

        verify(emailService, never()).sendReminder(any(), any(Integer.class));
    }

    @Test
    void shouldSendReminderForNewStatusAppointment() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(48)
                .build();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .status(AppointmentStatus.NEW)
                .client(Client.builder().email("client@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));
        when(emailService.wasAlreadySent(appointmentId, EmailType.REMINDER)).thenReturn(false);

        reminderScheduler.processReminders();

        verify(emailService).sendReminder(appointment, 48);
    }

    @Test
    void shouldSkipAftercareWhenAlreadySent() {
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
        when(emailService.wasAlreadySent(appointmentId, EmailType.AFTERCARE)).thenReturn(true);

        reminderScheduler.processReminders();

        verify(emailService, never()).sendAftercare(any());
    }

    @Test
    void shouldSkipAftercareWhenStatusNotDone() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(false)
                .emailAftercare(true)
                .build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().email("client@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(appointment));

        reminderScheduler.processReminders();

        verify(emailService, never()).sendAftercare(any());
    }

    @Test
    void shouldProcessBothRemindersAndAftercareWhenBothEnabled() {
        UUID tenantId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        UUID aftercareId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(true)
                .reminderHoursBefore(24)
                .build();
        Appointment reminderAppointment = Appointment.builder()
                .id(reminderId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().email("reminder@test.com").build())
                .build();
        Appointment aftercareAppointment = Appointment.builder()
                .id(aftercareId)
                .tenantId(tenantId)
                .status(AppointmentStatus.DONE)
                .client(Client.builder().email("aftercare@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(reminderAppointment))
                .thenReturn(List.of(aftercareAppointment));
        when(emailService.wasAlreadySent(reminderId, EmailType.REMINDER)).thenReturn(false);
        when(emailService.wasAlreadySent(aftercareId, EmailType.AFTERCARE)).thenReturn(false);

        reminderScheduler.processReminders();

        verify(emailService).sendReminder(reminderAppointment, 24);
        verify(emailService).sendAftercare(aftercareAppointment);
    }

    @Test
    void shouldContinueProcessingOtherTenantsWhenOneTenantFails() {
        UUID failingTenantId = UUID.randomUUID();
        UUID healthyTenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        CompanySettings failingSettings = CompanySettings.builder()
                .tenantId(failingTenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();
        CompanySettings healthySettings = CompanySettings.builder()
                .tenantId(healthyTenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();
        Appointment healthyAppointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(healthyTenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().email("client@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(failingSettings, healthySettings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(failingTenantId), any(Instant.class), any(Instant.class)))
                .thenThrow(new RuntimeException("db timeout"));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(healthyTenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(healthyAppointment));
        when(emailService.wasAlreadySent(appointmentId, EmailType.REMINDER)).thenReturn(false);

        reminderScheduler.processReminders();

        verify(emailService).sendReminder(healthyAppointment, 24);
    }

    @Test
    void shouldContinueWhenSendReminderFailsForSingleAppointment() {
        UUID tenantId = UUID.randomUUID();
        UUID failingId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();
        Appointment failingAppointment = Appointment.builder()
                .id(failingId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().email("fail@test.com").build())
                .build();
        Appointment healthyAppointment = Appointment.builder()
                .id(healthyId)
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().email("ok@test.com").build())
                .build();

        when(companySettingsRepository.findAll()).thenReturn(List.of(settings));
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(failingAppointment, healthyAppointment));
        when(emailService.wasAlreadySent(failingId, EmailType.REMINDER)).thenReturn(false);
        when(emailService.wasAlreadySent(healthyId, EmailType.REMINDER)).thenReturn(false);
        doThrow(new RuntimeException("smtp error"))
                .when(emailService).sendReminder(failingAppointment, 24);

        reminderScheduler.processReminders();

        verify(emailService).sendReminder(failingAppointment, 24);
        verify(emailService).sendReminder(healthyAppointment, 24);
    }
}
