package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationDispatchContext;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.appointment.support.AppointmentLabels;
import com.inkflow.crm.module.email.template.TemplateVars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TriggerVariableBuilder {

    private static final int HOURS_PER_DAY = 24;
    private static final String DAYS_SUFFIX = " дн.";
    private static final String HOURS_SUFFIX = " год.";

    public NotificationDispatchContext forClient(
            Appointment appointment,
            EmailTenantContext tenantContext,
            TriggerType triggerType,
            Integer offsetMinutes) {

        Map<String, String> variables = buildClientVariables(appointment, tenantContext, offsetMinutes);
        Client client = appointment.getClient();

        return NotificationDispatchContext.of(
                appointment.getTenantId(),
                client.getEmail(),
                client.getFullName(),
                appointment.getId(),
                variables,
                tenantContext.studioName(),
                tenantContext.studioLogoUrl()
        );
    }

    public NotificationDispatchContext forClient(
            Client client,
            EmailTenantContext tenantContext,
            TriggerType triggerType) {

        Map<String, String> variables = new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, client.getFirstName())
                .toMap();

        return NotificationDispatchContext.of(
                client.getTenantId(),
                client.getEmail(),
                client.getFullName(),
                client.getId(),
                variables,
                tenantContext.studioName(),
                tenantContext.studioLogoUrl()
        );
    }

    private Map<String, String> buildClientVariables(
            Appointment appointment,
            EmailTenantContext tenantContext,
            Integer offsetMinutes) {

        TemplateVars variables = new TemplateVars()
                .put(TemplateVar.CLIENT_NAME, appointment.getClient().getFirstName())
                .put(TemplateVar.MASTER_NAME, appointment.getArtist().getFullName())
                .put(TemplateVar.SERVICE, AppointmentLabels.serviceTitle(appointment))
                .put(TemplateVar.DATE, formatDate(appointment.getStartTime(), tenantContext))
                .put(TemplateVar.TIME, formatTime(appointment.getStartTime(), tenantContext.timezone()))
                .put(TemplateVar.ADDRESS, resolveAddress(appointment));

        if (offsetMinutes != null) {
            variables.put(TemplateVar.REMINDER_WINDOW, formatOffsetWindow(offsetMinutes));
        }

        return variables.toMap();
    }

    private String resolveAddress(Appointment appointment) {
        if (appointment.getLocation() == null) {
            return "";
        }
        String address = appointment.getLocation().getAddress();
        return address != null ? address : "";
    }

    private String formatOffsetWindow(int offsetMinutes) {
        int hours = offsetMinutes / 60;
        return hours >= HOURS_PER_DAY
                ? (hours / HOURS_PER_DAY) + DAYS_SUFFIX
                : hours + HOURS_SUFFIX;
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
