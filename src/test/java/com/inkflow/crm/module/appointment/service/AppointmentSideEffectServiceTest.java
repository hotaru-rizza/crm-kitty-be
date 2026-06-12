package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.audit.service.AuditLogService;
import com.inkflow.crm.module.appointment.dto.AppointmentUpdateContext;
import com.inkflow.crm.module.email.service.AppointmentNotificationService;
import com.inkflow.crm.module.google.service.GoogleCalendarSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentSideEffectServiceTest {

    @Mock
    private AppointmentNotificationService appointmentNotificationService;

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    @Mock
    private GoogleCalendarSyncService googleCalendarSyncService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AppointmentSideEffectService sideEffectService;

    @Test
    void afterCreate_sendsConfirmationAndSyncsCalendar() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailConfirmations(true)
                .emailStaffNewAppointment(false)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterCreate(appointment);

        verify(appointmentNotificationService).sendConfirmation(appointment);
        verify(googleCalendarSyncService).syncNewAppointment(appointment);
        verify(auditLogService).logCurrent("CREATE", "APPOINTMENT", appointment.getId().toString(),
                "John Doe @ " + appointment.getStartTime());
    }

    @Test
    void afterDelete_syncsCalendarDeletion() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);

        sideEffectService.afterDelete(appointment, appointmentId);

        verify(googleCalendarSyncService).syncDeletedAppointment(appointment);
        verify(auditLogService).logCurrent("DELETE", "APPOINTMENT", appointmentId.toString(), "John Doe");
    }

    @Test
    void afterUpdate_onConfirmed_sendsConfirmationEmail() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailConfirmations(true)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.NEW, "confirmed", false));

        verify(appointmentNotificationService).sendConfirmation(appointment);
        verify(googleCalendarSyncService).syncUpdatedAppointment(appointment);
    }

    @Test
    void afterUpdate_onDone_sendsAftercareEmail() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);
        appointment.setStatus(AppointmentStatus.DONE);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailAftercare(true)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.CONFIRMED, "done", false));

        verify(appointmentNotificationService).sendAftercare(appointment);
    }

    @Test
    void afterUpdate_onCancelled_syncsCalendarDeletion() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);
        appointment.setStatus(AppointmentStatus.CANCELLED);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailCancellation(true)
                .emailStaffCancellation(false)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.CONFIRMED, "cancelled", false));

        verify(appointmentNotificationService).sendCancellation(appointment);
        verify(googleCalendarSyncService).syncDeletedAppointment(appointment);
        verify(googleCalendarSyncService, never()).syncUpdatedAppointment(appointment);
    }

    @Test
    void afterUpdate_onReschedule_sendsRescheduleEmails() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReschedule(true)
                .emailStaffReschedule(true)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.NEW, "new", true));

        verify(appointmentNotificationService).sendReschedule(appointment);
        verify(appointmentNotificationService).sendStaffReschedule(appointment);
        verify(googleCalendarSyncService).syncUpdatedAppointment(appointment);
    }

    @Test
    void afterCreate_sendsStaffNewAppointmentEmailWhenEnabled() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailConfirmations(false)
                .emailStaffNewAppointment(true)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterCreate(appointment);

        verify(appointmentNotificationService).sendStaffNewAppointment(appointment);
        verify(appointmentNotificationService, never()).sendConfirmation(appointment);
        verify(googleCalendarSyncService).syncNewAppointment(appointment);
    }

    @Test
    void afterUpdate_onCancelled_sendsStaffCancellationEmail() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);
        appointment.setStatus(AppointmentStatus.CANCELLED);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailCancellation(false)
                .emailStaffCancellation(true)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.CONFIRMED, "cancelled", false));

        verify(appointmentNotificationService, never()).sendCancellation(appointment);
        verify(appointmentNotificationService).sendStaffCancellation(appointment);
        verify(googleCalendarSyncService).syncDeletedAppointment(appointment);
    }

    @Test
    void afterUpdate_skipsStatusEmailsWhenFlagsDisabled() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);
        appointment.setStatus(AppointmentStatus.DONE);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailAftercare(false)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.CONFIRMED, "done", false));

        verifyNoInteractions(appointmentNotificationService);
        verify(googleCalendarSyncService).syncUpdatedAppointment(appointment);
    }

    @Test
    void afterUpdate_skipsEmailsWhenRequestedStatusNull() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReschedule(true)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        sideEffectService.afterUpdate(appointment, new AppointmentUpdateContext(
                AppointmentStatus.NEW, null, true));

        verify(appointmentNotificationService, never()).sendReschedule(appointment);
        verify(googleCalendarSyncService).syncUpdatedAppointment(appointment);
    }

    @Test
    void afterCreate_skipsEmailsWhenSettingsMissing() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = appointment(tenantId);

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        sideEffectService.afterCreate(appointment);

        verify(appointmentNotificationService, never()).sendConfirmation(appointment);
        verify(googleCalendarSyncService).syncNewAppointment(appointment);
    }

    private Appointment appointment(UUID tenantId) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.NEW)
                .startTime(Instant.parse("2026-06-15T10:00:00Z"))
                .client(Client.builder().firstName("John").lastName("Doe").build())
                .build();
    }
}
