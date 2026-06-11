package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.PreparedEmail;
import com.inkflow.crm.module.email.mapper.EmailTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AppointmentEmailComposer {

    private final EmailTemplateMapper emailTemplateMapper;

    public PreparedEmail confirmation(
            Appointment appointment,
            EmailTenantContext context,
            Map<String, String> templateEntry) {
        String clientName = appointment.getClient().getFirstName();
        String artistName = fullName(appointment.getArtist());
        String serviceTitle = appointment.getService().getTitle();
        List<String> fields = emailTemplateMapper.resolveActiveFields(templateEntry, "CONFIRMATION");

        String html = EmailTemplates.confirmation(
                clientName,
                serviceTitle,
                artistName,
                appointment.getStartTime(),
                context.timezone(),
                context.studioName(),
                customValue(templateEntry, "subject"),
                customValue(templateEntry, "body"),
                fields
        );

        return new PreparedEmail(
                appointment.getClient().getEmail(),
                appointment.getClient().getFullName(),
                emailTemplateMapper.resolveSubject(templateEntry, "CONFIRMATION", context.studioName()),
                html
        );
    }

    public PreparedEmail reminder(
            Appointment appointment,
            EmailTenantContext context,
            Map<String, String> templateEntry,
            int hoursBefore) {
        String clientName = appointment.getClient().getFirstName();
        String artistName = fullName(appointment.getArtist());
        String serviceTitle = appointment.getService().getTitle();
        List<String> fields = emailTemplateMapper.resolveActiveFields(templateEntry, "REMINDER");

        String html = EmailTemplates.reminder(
                clientName,
                serviceTitle,
                artistName,
                appointment.getStartTime(),
                context.timezone(),
                context.studioName(),
                hoursBefore,
                customValue(templateEntry, "subject"),
                customValue(templateEntry, "body"),
                fields
        );

        return new PreparedEmail(
                appointment.getClient().getEmail(),
                appointment.getClient().getFullName(),
                emailTemplateMapper.resolveSubject(templateEntry, "REMINDER", context.studioName()),
                html
        );
    }

    public PreparedEmail aftercare(
            Appointment appointment,
            EmailTenantContext context,
            Map<String, String> templateEntry) {
        String clientName = appointment.getClient().getFirstName();
        String serviceTitle = appointment.getService().getTitle();
        List<String> fields = emailTemplateMapper.resolveActiveFields(templateEntry, "AFTERCARE");

        String html = EmailTemplates.aftercare(
                clientName,
                serviceTitle,
                context.studioName(),
                customValue(templateEntry, "subject"),
                customValue(templateEntry, "body"),
                fields
        );

        return new PreparedEmail(
                appointment.getClient().getEmail(),
                appointment.getClient().getFullName(),
                emailTemplateMapper.resolveSubject(templateEntry, "AFTERCARE", context.studioName()),
                html
        );
    }

    public PreparedEmail cancellation(
            Appointment appointment,
            EmailTenantContext context,
            Map<String, String> templateEntry) {
        String html = EmailTemplates.cancellation(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getStartTime(),
                context.timezone(),
                context.studioName(),
                customValue(templateEntry, "subject"),
                customValue(templateEntry, "body")
        );

        return new PreparedEmail(
                appointment.getClient().getEmail(),
                appointment.getClient().getFullName(),
                emailTemplateMapper.resolveSubject(templateEntry, "CANCELLATION", context.studioName()),
                html
        );
    }

    public PreparedEmail reschedule(
            Appointment appointment,
            EmailTenantContext context,
            Map<String, String> templateEntry) {
        String html = EmailTemplates.reschedule(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                fullName(appointment.getArtist()),
                appointment.getStartTime(),
                context.timezone(),
                context.studioName(),
                customValue(templateEntry, "subject"),
                customValue(templateEntry, "body")
        );

        return new PreparedEmail(
                appointment.getClient().getEmail(),
                appointment.getClient().getFullName(),
                emailTemplateMapper.resolveSubject(templateEntry, "RESCHEDULE", context.studioName()),
                html
        );
    }

    public PreparedEmail staffNewAppointment(Appointment appointment, EmailTenantContext context) {
        String clientName = appointment.getClient().getFullName();
        String html = EmailTemplates.staffNewAppointment(
                appointment.getArtist().getFirstName(),
                clientName,
                appointment.getService().getTitle(),
                appointment.getStartTime(),
                context.timezone(),
                context.studioName()
        );

        return new PreparedEmail(
                appointment.getArtist().getEmail(),
                fullName(appointment.getArtist()),
                "Новий запис — " + clientName,
                html
        );
    }

    public PreparedEmail staffCancellation(Appointment appointment, EmailTenantContext context) {
        String clientName = appointment.getClient().getFullName();
        String html = EmailTemplates.staffCancellation(
                appointment.getArtist().getFirstName(),
                clientName,
                appointment.getService().getTitle(),
                appointment.getStartTime(),
                context.timezone(),
                context.studioName()
        );

        return new PreparedEmail(
                appointment.getArtist().getEmail(),
                fullName(appointment.getArtist()),
                "Запис скасовано — " + clientName,
                html
        );
    }

    public PreparedEmail staffReschedule(Appointment appointment, EmailTenantContext context) {
        String clientName = appointment.getClient().getFullName();
        String html = EmailTemplates.staffReschedule(
                appointment.getArtist().getFirstName(),
                clientName,
                appointment.getService().getTitle(),
                appointment.getStartTime(),
                context.timezone(),
                context.studioName()
        );

        return new PreparedEmail(
                appointment.getArtist().getEmail(),
                fullName(appointment.getArtist()),
                "Час запису змінено — " + clientName,
                html
        );
    }

    private String customValue(Map<String, String> templateEntry, String key) {
        return templateEntry != null ? templateEntry.get(key) : null;
    }

    private String fullName(Staff staff) {
        return staff.getFirstName() + " " + staff.getLastName();
    }
}
