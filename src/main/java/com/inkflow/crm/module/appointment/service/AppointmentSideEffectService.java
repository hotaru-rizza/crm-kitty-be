package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.appointment.dto.AppointmentUpdateContext;
import com.inkflow.crm.module.audit.AuditLogService;
import com.inkflow.crm.module.email.service.EmailService;
import com.inkflow.crm.module.google.GoogleCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentSideEffectService {

    private final EmailService emailService;
    private final CompanySettingsRepository companySettingsRepository;
    private final GoogleCalendarSyncService googleCalendarSyncService;
    private final AuditLogService auditLogService;

    public void afterCreate(Appointment appointment) {
        log.info("Appointment side-effects after create: appointmentId={} tenantId={}",
                appointment.getId(), appointment.getTenantId());

        sendCreateEmails(appointment);
        googleCalendarSyncService.syncNewAppointment(appointment);
        auditCreated(appointment);
    }

    public void afterUpdate(Appointment appointment, AppointmentUpdateContext context) {
        log.info("Appointment side-effects after update: appointmentId={} tenantId={}",
                appointment.getId(), appointment.getTenantId());

        sendUpdateEmails(appointment, context);
        syncCalendarAfterUpdate(appointment);
    }

    public void afterDelete(Appointment appointment, UUID appointmentId) {
        log.info("Appointment side-effects after delete: appointmentId={} tenantId={}",
                appointmentId, appointment.getTenantId());

        googleCalendarSyncService.syncDeletedAppointment(appointment);
        auditDeleted(appointment, appointmentId);
    }

    private void sendCreateEmails(Appointment appointment) {
        try {
            CompanySettings settings = settingsFor(appointment.getTenantId());
            if (settings == null) {
                return;
            }

            if (settings.getEmailConfirmations()) {
                emailService.sendConfirmation(appointment);
            }
            if (Boolean.TRUE.equals(settings.getEmailStaffNewAppointment())) {
                emailService.sendStaffNewAppointment(appointment);
            }
        } catch (Exception e) {
            log.warn("Email side-effect failed on appointment create {} tenant {}: {}",
                    appointment.getId(), appointment.getTenantId(), e.getMessage());
        }
    }

    private void sendUpdateEmails(Appointment appointment, AppointmentUpdateContext context) {
        try {
            CompanySettings settings = settingsFor(appointment.getTenantId());
            if (settings == null || context.requestedStatus() == null) {
                return;
            }

            AppointmentStatus newStatus = AppointmentStatus.fromValue(context.requestedStatus());
            sendStatusChangeEmails(appointment, settings, context.previousStatus(), newStatus);

            if (context.startTimeChanged()) {
                sendRescheduleEmails(appointment, settings);
            }
        } catch (Exception e) {
            log.warn("Email side-effect failed on appointment update {} tenant {}: {}",
                    appointment.getId(), appointment.getTenantId(), e.getMessage());
        }
    }

    private void sendStatusChangeEmails(
            Appointment appointment,
            CompanySettings settings,
            AppointmentStatus previousStatus,
            AppointmentStatus newStatus) {
        if (newStatus == AppointmentStatus.CONFIRMED
                && previousStatus != AppointmentStatus.CONFIRMED
                && settings.getEmailConfirmations()
                && !emailService.wasAlreadySent(appointment.getId(), EmailType.CONFIRMATION)) {
            emailService.sendConfirmation(appointment);
        }

        if (newStatus == AppointmentStatus.DONE
                && previousStatus != AppointmentStatus.DONE
                && settings.getEmailAftercare()) {
            emailService.sendAftercare(appointment);
        }

        if (newStatus != AppointmentStatus.CANCELLED || previousStatus == AppointmentStatus.CANCELLED) {
            return;
        }

        if (Boolean.TRUE.equals(settings.getEmailCancellation())) {
            emailService.sendCancellation(appointment);
        }
        if (Boolean.TRUE.equals(settings.getEmailStaffCancellation())) {
            emailService.sendStaffCancellation(appointment);
        }
    }

    private void sendRescheduleEmails(Appointment appointment, CompanySettings settings) {
        if (Boolean.TRUE.equals(settings.getEmailReschedule())) {
            emailService.sendReschedule(appointment);
        }
        if (Boolean.TRUE.equals(settings.getEmailStaffReschedule())) {
            emailService.sendStaffReschedule(appointment);
        }
    }

    private void syncCalendarAfterUpdate(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            googleCalendarSyncService.syncDeletedAppointment(appointment);
            return;
        }
        googleCalendarSyncService.syncUpdatedAppointment(appointment);
    }

    private void auditCreated(Appointment appointment) {
        String label = appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName()
                + " @ " + appointment.getStartTime();
        auditLogService.logCurrent("CREATE", "APPOINTMENT", appointment.getId().toString(), label);
    }

    private void auditDeleted(Appointment appointment, UUID appointmentId) {
        String label = appointment.getClient() != null
                ? appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName()
                : appointmentId.toString();
        auditLogService.logCurrent("DELETE", "APPOINTMENT", appointmentId.toString(), label);
    }

    private CompanySettings settingsFor(UUID tenantId) {
        return companySettingsRepository.findByTenantId(tenantId).orElse(null);
    }
}
