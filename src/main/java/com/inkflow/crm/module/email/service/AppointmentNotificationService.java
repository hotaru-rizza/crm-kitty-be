package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.template.TemplateVars;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentNotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("uk"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", new Locale("uk"));

    private final NotificationSender notificationSender;
    private final EmailTenantContextLoader tenantContextLoader;

    public void sendConfirmation(Appointment appointment) {
        if (!hasClientEmail(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_CONFIRMED, clientAppointmentVars(appointment, context));
    }

    public void sendReminder(Appointment appointment, int hoursBefore) {
        if (!hasClientEmail(appointment)) {
            return;
        }
        if (notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, appointment.getId())) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_REMINDER, reminderVars(appointment, context, hoursBefore));
    }

    public void sendAftercare(Appointment appointment) {
        if (!hasClientEmail(appointment)) {
            return;
        }
        if (notificationSender.wasAlreadySent(TemplateKey.AFTERCARE_INSTRUCTIONS, appointment.getId())) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.AFTERCARE_INSTRUCTIONS, basicClientVars(appointment));
    }

    public void sendCancellation(Appointment appointment) {
        if (!hasClientEmail(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_CANCELED, clientAppointmentVars(appointment, context));
    }

    public void sendReschedule(Appointment appointment) {
        if (!hasClientEmail(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_RESCHEDULED, clientAppointmentVars(appointment, context));
    }

    public void sendStaffNewAppointment(Appointment appointment) {
        if (!hasStaffEmail(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToStaff(appointment, context, TemplateKey.NEW_APPOINTMENT, staffAppointmentVars(appointment, context));
    }

    public void sendStaffCancellation(Appointment appointment) {
        if (!hasStaffEmail(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToStaff(appointment, context, TemplateKey.APPOINTMENT_CANCELED, staffAppointmentVars(appointment, context));
    }

    public void sendStaffReschedule(Appointment appointment) {
        if (!hasStaffEmail(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToStaff(appointment, context, TemplateKey.APPOINTMENT_CHANGED, staffAppointmentVars(appointment, context));
    }

    private void sendToClient(Appointment appointment, EmailTenantContext context, TemplateKey templateKey,
                              Map<String, String> variables) {
        notificationSender.send(NotificationCommand.forTenant(
                appointment.getTenantId(),
                EmailRecipient.of(appointment.getClient().getEmail(), appointment.getClient().getFullName()),
                templateKey,
                variables,
                appointment.getId(),
                context.studioName()
        ));
    }

    private void sendToStaff(Appointment appointment, EmailTenantContext context, TemplateKey templateKey,
                             Map<String, String> variables) {
        Staff artist = appointment.getArtist();

        notificationSender.send(NotificationCommand.forTenant(
                appointment.getTenantId(),
                EmailRecipient.of(artist.getEmail(), fullName(artist)),
                templateKey,
                variables,
                appointment.getId(),
                context.studioName()
        ));
    }

    private Map<String, String> clientAppointmentVars(Appointment appointment, EmailTenantContext context) {
        TemplateVars variables = new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, fullName(appointment.getArtist()))
                .put(TemplateVar.SERVICE, appointment.getService().getTitle());

        putSchedule(variables, appointment, context);
        return variables.toMap();
    }

    private Map<String, String> reminderVars(Appointment appointment, EmailTenantContext context, int hoursBefore) {
        String window = hoursBefore >= 24 ? (hoursBefore / 24) + " дн." : hoursBefore + " год.";

        return new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, fullName(appointment.getArtist()))
                .put(TemplateVar.TIME, formatTime(appointment.getStartTime(), context.timezone()))
                .put(TemplateVar.REMINDER_WINDOW, window)
                .toMap();
    }

    private Map<String, String> basicClientVars(Appointment appointment) {
        return new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, fullName(appointment.getArtist()))
                .put(TemplateVar.SERVICE, appointment.getService().getTitle())
                .toMap();
    }

    private Map<String, String> staffAppointmentVars(Appointment appointment, EmailTenantContext context) {
        TemplateVars variables = new TemplateVars()
                .put(TemplateVar.MASTER_NAME, appointment.getArtist().getFirstName())
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFullName())
                .put(TemplateVar.SERVICE, appointment.getService().getTitle());

        putSchedule(variables, appointment, context);
        return variables.toMap();
    }

    private void putSchedule(TemplateVars variables, Appointment appointment, EmailTenantContext context) {
        variables.put(TemplateVar.DATE, formatDate(appointment.getStartTime(), context.timezone()));
        variables.put(TemplateVar.TIME, formatTime(appointment.getStartTime(), context.timezone()));
    }

    private boolean hasClientEmail(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) {
            log.debug("Skipping client email for appointment {}: no client email", appointment.getId());
            return false;
        }
        return true;
    }

    private boolean hasStaffEmail(Appointment appointment) {
        Staff artist = appointment.getArtist();
        if (artist == null || artist.getEmail() == null || artist.getEmail().isBlank()) {
            log.debug("Skipping staff email for appointment {}: no artist email", appointment.getId());
            return false;
        }
        return true;
    }

    private EmailTenantContext loadContext(Appointment appointment) {
        return tenantContextLoader.loadContext(appointment.getTenantId());
    }

    private String fullName(Staff staff) {
        return staff.getFirstName() + " " + staff.getLastName();
    }

    private String formatDate(Instant time, String timezone) {
        return time.atZone(ZoneId.of(timezone)).format(DATE_FMT);
    }

    private String formatTime(Instant time, String timezone) {
        return time.atZone(ZoneId.of(timezone)).format(TIME_FMT);
    }
}
