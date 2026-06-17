package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.module.appointment.dto.AppointmentUpdateContext;
import com.inkflow.crm.module.appointment.event.AppointmentCanceledEvent;
import com.inkflow.crm.module.appointment.event.AppointmentCompletedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentConfirmedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentRescheduledEvent;
import com.inkflow.crm.module.audit.service.AuditLogService;
import com.inkflow.crm.module.email.service.sending.AppointmentNotificationService;
import com.inkflow.crm.module.google.service.GoogleCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentSideEffectService {

    private final AppointmentNotificationService appointmentNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final GoogleCalendarSyncService googleCalendarSyncService;
    private final AuditLogService auditLogService;

    public void afterCreate(Appointment appointment) {
        log.info("Appointment side-effects after create: appointmentId={} tenantId={}",
                appointment.getId(), appointment.getTenantId());

        publishConfirmedEvent(appointment);
        sendStaffNewAppointmentSafely(appointment);
        syncCalendarSafely(appointment, () -> googleCalendarSyncService.syncNewAppointment(appointment));
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

        syncCalendarSafely(appointment, () -> googleCalendarSyncService.syncDeletedAppointment(appointment));
        auditDeleted(appointment, appointmentId);
    }

    private void publishConfirmedEvent(Appointment appointment) {
        try {
            eventPublisher.publishEvent(new AppointmentConfirmedEvent(
                    appointment.getId(), appointment.getTenantId()));
        } catch (Exception exception) {
            log.warn("Failed to publish appointment confirmed event {}: {}",
                    appointment.getId(), exception.getMessage());
        }
    }

    private void sendStaffNewAppointmentSafely(Appointment appointment) {
        try {
            appointmentNotificationService.sendStaffNewAppointment(appointment);
        } catch (Exception exception) {
            log.warn("Staff new appointment email failed {}: {}", appointment.getId(), exception.getMessage());
        }
    }

    private void sendUpdateEmails(Appointment appointment, AppointmentUpdateContext context) {
        try {
            if (context.requestedStatus() != null) {
                AppointmentStatus newStatus = AppointmentStatus.fromValue(context.requestedStatus());
                publishStatusChangeEvents(appointment, context.previousStatus(), newStatus);
                sendStaffStatusChangeEmails(appointment, context.previousStatus(), newStatus);
            }

            if (context.startTimeChanged()) {
                publishRescheduledEvent(appointment);
                sendStaffRescheduleSafely(appointment);
            }
        } catch (Exception exception) {
            log.warn("Email side-effect failed on appointment update {} tenant {}: {}",
                    appointment.getId(), appointment.getTenantId(), exception.getMessage());
        }
    }

    private void publishStatusChangeEvents(
            Appointment appointment,
            AppointmentStatus previousStatus,
            AppointmentStatus newStatus) {

        UUID tenantId = appointment.getTenantId();
        UUID appointmentId = appointment.getId();

        if (newStatus == AppointmentStatus.COMPLETED && previousStatus != AppointmentStatus.COMPLETED) {
            eventPublisher.publishEvent(new AppointmentCompletedEvent(appointmentId, tenantId));
        }

        if (newStatus == AppointmentStatus.CANCELLED && previousStatus != AppointmentStatus.CANCELLED) {
            eventPublisher.publishEvent(new AppointmentCanceledEvent(appointmentId, tenantId));
        }
    }

    private void sendStaffStatusChangeEmails(
            Appointment appointment,
            AppointmentStatus previousStatus,
            AppointmentStatus newStatus) {

        if (newStatus != AppointmentStatus.CANCELLED || previousStatus == AppointmentStatus.CANCELLED) {
            return;
        }

        appointmentNotificationService.sendStaffCancellation(appointment);
    }

    private void publishRescheduledEvent(Appointment appointment) {
        eventPublisher.publishEvent(new AppointmentRescheduledEvent(
                appointment.getId(), appointment.getTenantId()));
    }

    private void sendStaffRescheduleSafely(Appointment appointment) {
        try {
            appointmentNotificationService.sendStaffReschedule(appointment);
        } catch (Exception exception) {
            log.warn("Staff reschedule email failed {}: {}", appointment.getId(), exception.getMessage());
        }
    }

    private void syncCalendarAfterUpdate(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            syncCalendarSafely(appointment, () -> googleCalendarSyncService.syncDeletedAppointment(appointment));
            return;
        }
        syncCalendarSafely(appointment, () -> googleCalendarSyncService.syncUpdatedAppointment(appointment));
    }

    private void syncCalendarSafely(Appointment appointment, Runnable syncAction) {
        try {
            syncAction.run();
        } catch (Exception exception) {
            log.warn("Google Calendar sync dispatch failed for appointment {} tenant {}: {}",
                    appointment.getId(), appointment.getTenantId(), exception.getMessage());
        }
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
}
