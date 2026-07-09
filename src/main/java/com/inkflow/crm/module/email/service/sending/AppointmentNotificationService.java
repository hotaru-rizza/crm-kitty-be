package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.service.EmailTenantContextLoader;
import com.inkflow.crm.module.appointment.support.AppointmentLabels;
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

    private static final int HOURS_PER_DAY = 24;
    private static final String DAYS_SUFFIX = " дн.";
    private static final String HOURS_SUFFIX = " год.";

    private final NotificationSender notificationSender;
    private final EmailTenantContextLoader tenantContextLoader;

    public void sendConfirmation(Appointment appointment) {
        if (clientEmailMissing(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_CONFIRMED, clientAppointmentVars(appointment, context));
    }

    public void sendReminder(Appointment appointment, int hoursBefore) {
        if (clientEmailMissing(appointment)) {
            return;
        }
        if (notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, appointment.getId())) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_REMINDER, reminderVars(appointment, context, hoursBefore));
    }

    public void sendAftercare(Appointment appointment) {
        if (clientEmailMissing(appointment)) {
            return;
        }
        if (notificationSender.wasAlreadySent(TemplateKey.AFTERCARE_INSTRUCTIONS, appointment.getId())) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.AFTERCARE_INSTRUCTIONS, basicClientVars(appointment));
    }

    public void sendCancellation(Appointment appointment) {
        if (clientEmailMissing(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_CANCELED, clientAppointmentVars(appointment, context));
    }

    public void sendReschedule(Appointment appointment) {
        if (clientEmailMissing(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToClient(appointment, context, TemplateKey.BOOKING_RESCHEDULED, clientAppointmentVars(appointment, context));
    }

    public void sendStaffNewAppointment(Appointment appointment) {
        if (staffEmailMissing(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToStaff(appointment, context, TemplateKey.NEW_APPOINTMENT, staffAppointmentVars(appointment, context));
    }

    public void sendStaffCancellation(Appointment appointment) {
        if (staffEmailMissing(appointment)) {
            return;
        }

        EmailTenantContext context = loadContext(appointment);
        sendToStaff(appointment, context, TemplateKey.APPOINTMENT_CANCELED, staffAppointmentVars(appointment, context));
    }

    public void sendStaffReschedule(Appointment appointment) {
        if (staffEmailMissing(appointment)) {
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
                context
        ));
    }

    private void sendToStaff(Appointment appointment, EmailTenantContext context, TemplateKey templateKey,
                             Map<String, String> variables) {
        Staff artist = appointment.getArtist();

        notificationSender.send(NotificationCommand.forTenant(
                appointment.getTenantId(),
                EmailRecipient.of(artist.getEmail(), artist.getFullName()),
                templateKey,
                variables,
                appointment.getId(),
                context
        ));
    }

    private Map<String, String> clientAppointmentVars(Appointment appointment, EmailTenantContext context) {
        TemplateVars variables = new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, appointment.getArtist().getFullName())
                .put(TemplateVar.SERVICE, AppointmentLabels.serviceTitle(appointment));

        putSchedule(variables, appointment, context);
        return variables.toMap();
    }

    private Map<String, String> reminderVars(Appointment appointment, EmailTenantContext context, int hoursBefore) {
        return new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, appointment.getArtist().getFullName())
                .put(TemplateVar.TIME, formatTime(appointment.getStartTime(), context.timezone()))
                .put(TemplateVar.REMINDER_WINDOW, formatReminderWindow(hoursBefore))
                .toMap();
    }

    private Map<String, String> basicClientVars(Appointment appointment) {
        return new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, appointment.getArtist().getFullName())
                .put(TemplateVar.SERVICE, AppointmentLabels.serviceTitle(appointment))
                .toMap();
    }

    private Map<String, String> staffAppointmentVars(Appointment appointment, EmailTenantContext context) {
        TemplateVars variables = new TemplateVars()
                .put(TemplateVar.MASTER_NAME, appointment.getArtist().getFirstName())
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFullName())
                .put(TemplateVar.SERVICE, AppointmentLabels.serviceTitle(appointment));

        putSchedule(variables, appointment, context);
        return variables.toMap();
    }

    private void putSchedule(TemplateVars variables, Appointment appointment, EmailTenantContext context) {
        variables.put(TemplateVar.DATE, formatDate(appointment.getStartTime(), context));
        variables.put(TemplateVar.TIME, formatTime(appointment.getStartTime(), context.timezone()));
    }

    private boolean clientEmailMissing(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) {
            log.debug("Skipping client email for appointment {}: no client email", appointment.getId());
            return true;
        }
        return false;
    }

    private boolean staffEmailMissing(Appointment appointment) {
        Staff artist = appointment.getArtist();
        if (artist == null || artist.getEmail() == null || artist.getEmail().isBlank()) {
            log.debug("Skipping staff email for appointment {}: no artist email", appointment.getId());
            return true;
        }
        return false;
    }

    private EmailTenantContext loadContext(Appointment appointment) {
        return tenantContextLoader.loadContext(appointment.getTenantId());
    }

    private String formatReminderWindow(int hoursBefore) {
        return hoursBefore >= HOURS_PER_DAY
                ? (hoursBefore / HOURS_PER_DAY) + DAYS_SUFFIX
                : hoursBefore + HOURS_SUFFIX;
    }

    private String formatDate(Instant time, EmailTenantContext context) {
        Locale locale = Locale.forLanguageTag(context.locale().getCode());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale);
        return time.atZone(ZoneId.of(context.timezone())).format(formatter);
    }

    private String formatTime(Instant time, String timezone) {
        return time.atZone(ZoneId.of(timezone)).format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
