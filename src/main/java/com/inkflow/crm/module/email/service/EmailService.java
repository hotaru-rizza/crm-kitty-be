package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ResendEmailClient resendClient;
    private final EmailLogRepository emailLogRepository;
    private final TenantRepository tenantRepository;
    private final CompanySettingsRepository companySettingsRepository;

    private Map<String, String> getCustomTemplate(UUID tenantId, String type) {
        return companySettingsRepository.findByTenantId(tenantId)
                .map(CompanySettings::getEmailTemplates)
                .map(t -> t != null ? t.get(type) : null)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<String> getCustomFields(UUID tenantId, String type) {
        return companySettingsRepository.findByTenantId(tenantId)
                .map(CompanySettings::getEmailTemplates)
                .map(t -> {
                    if (t == null) return null;
                    Map<String, String> tmpl = t.get(type);
                    if (tmpl == null || !tmpl.containsKey("fields")) return null;
                    String raw = tmpl.get("fields");
                    if (raw == null || raw.isBlank()) return List.<String>of();
                    return List.of(raw.split(","));
                })
                .orElse(null);
    }

    public void sendConfirmation(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) return;

        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";

        Map<String, String> custom = getCustomTemplate(appointment.getTenantId(), "CONFIRMATION");
        List<String> customFields = getCustomFields(appointment.getTenantId(), "CONFIRMATION");
        String customSubject = custom != null ? custom.get("subject") : null;
        String customBody = custom != null ? custom.get("body") : null;

        String html = EmailTemplates.confirmation(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                appointment.getStartTime(),
                timezone,
                studioName,
                customSubject,
                customBody,
                customFields
        );

        String subjectLine = customSubject != null
                ? customSubject.replace("{{studio}}", studioName)
                : "Запис підтверджено — " + studioName;

        sendAndLog(
                appointment.getTenantId(),
                email,
                appointment.getClient().getFullName(),
                subjectLine,
                html,
                EmailType.CONFIRMATION,
                appointment.getId()
        );
    }

    public void sendReminder(Appointment appointment, int hoursBefore) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) return;

        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";

        Map<String, String> custom = getCustomTemplate(appointment.getTenantId(), "REMINDER");
        List<String> customFields = getCustomFields(appointment.getTenantId(), "REMINDER");
        String customSubject = custom != null ? custom.get("subject") : null;
        String customBody = custom != null ? custom.get("body") : null;

        String html = EmailTemplates.reminder(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                appointment.getStartTime(),
                timezone,
                studioName,
                hoursBefore,
                customSubject,
                customBody,
                customFields
        );

        String subjectLine = customSubject != null
                ? customSubject.replace("{{studio}}", studioName)
                : "Нагадування про запис — " + studioName;

        sendAndLog(
                appointment.getTenantId(),
                email,
                appointment.getClient().getFullName(),
                subjectLine,
                html,
                EmailType.REMINDER,
                appointment.getId()
        );
    }

    public void sendAftercare(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) return;

        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";

        Map<String, String> custom = getCustomTemplate(appointment.getTenantId(), "AFTERCARE");
        List<String> customFields = getCustomFields(appointment.getTenantId(), "AFTERCARE");
        String customSubject = custom != null ? custom.get("subject") : null;
        String customBody = custom != null ? custom.get("body") : null;

        String html = EmailTemplates.aftercare(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                studioName,
                customSubject,
                customBody,
                customFields
        );

        String subjectLine = customSubject != null
                ? customSubject.replace("{{studio}}", studioName)
                : "Догляд після сеансу — " + studioName;

        sendAndLog(
                appointment.getTenantId(),
                email,
                appointment.getClient().getFullName(),
                subjectLine,
                html,
                EmailType.AFTERCARE,
                appointment.getId()
        );
    }

    public void sendCancellation(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) return;
        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";
        Map<String, String> custom = getCustomTemplate(appointment.getTenantId(), "CANCELLATION");
        String customSubject = custom != null ? custom.get("subject") : null;
        String customBody = custom != null ? custom.get("body") : null;
        String html = EmailTemplates.cancellation(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getStartTime(), timezone, studioName, customSubject, customBody);
        String subject = customSubject != null ? customSubject.replace("{{studio}}", studioName)
                : "Запис скасовано — " + studioName;
        sendAndLog(appointment.getTenantId(), email, appointment.getClient().getFullName(),
                subject, html, EmailType.CANCELLATION, appointment.getId());
    }

    public void sendReschedule(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) return;
        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";
        Map<String, String> custom = getCustomTemplate(appointment.getTenantId(), "RESCHEDULE");
        String customSubject = custom != null ? custom.get("subject") : null;
        String customBody = custom != null ? custom.get("body") : null;
        String html = EmailTemplates.reschedule(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                appointment.getStartTime(), timezone, studioName, customSubject, customBody);
        String subject = customSubject != null ? customSubject.replace("{{studio}}", studioName)
                : "Час запису змінено — " + studioName;
        sendAndLog(appointment.getTenantId(), email, appointment.getClient().getFullName(),
                subject, html, EmailType.RESCHEDULE, appointment.getId());
    }

    public void sendStaffNewAppointment(Appointment appointment) {
        String email = appointment.getArtist().getEmail();
        if (email == null || email.isBlank()) return;
        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";
        String html = EmailTemplates.staffNewAppointment(
                appointment.getArtist().getFirstName(),
                appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
                appointment.getService().getTitle(),
                appointment.getStartTime(), timezone, studioName);
        sendAndLog(appointment.getTenantId(), email,
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                "Новий запис — " + appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
                html, EmailType.STAFF_NEW_APPOINTMENT, appointment.getId());
    }

    public void sendStaffCancellation(Appointment appointment) {
        String email = appointment.getArtist().getEmail();
        if (email == null || email.isBlank()) return;
        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";
        String html = EmailTemplates.staffCancellation(
                appointment.getArtist().getFirstName(),
                appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
                appointment.getService().getTitle(),
                appointment.getStartTime(), timezone, studioName);
        sendAndLog(appointment.getTenantId(), email,
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                "Запис скасовано — " + appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
                html, EmailType.STAFF_CANCELLATION, appointment.getId());
    }

    public void sendStaffReschedule(Appointment appointment) {
        String email = appointment.getArtist().getEmail();
        if (email == null || email.isBlank()) return;
        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";
        String html = EmailTemplates.staffReschedule(
                appointment.getArtist().getFirstName(),
                appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
                appointment.getService().getTitle(),
                appointment.getStartTime(), timezone, studioName);
        sendAndLog(appointment.getTenantId(), email,
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                "Час запису змінено — " + appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName(),
                html, EmailType.STAFF_RESCHEDULE, appointment.getId());
    }

    public void sendManual(UUID tenantId, String recipientEmail, String recipientName, String subject, String textBody) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";

        String html = EmailTemplates.manual(subject, textBody, studioName);
        sendAndLog(tenantId, recipientEmail, recipientName, subject, html, EmailType.MANUAL, null);
    }

    public boolean wasAlreadySent(UUID appointmentId, EmailType type) {
        return emailLogRepository.existsByAppointmentIdAndType(appointmentId, type);
    }

    @Transactional(readOnly = true)
    public Page<EmailLogDto> getLog(UUID tenantId, EmailType type, Instant from, Instant to, Pageable pageable) {
        return emailLogRepository.findFiltered(tenantId, type, from, to, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public EmailStatsDto getStats(UUID tenantId) {
        Instant now = Instant.now();
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant weekStart = now.minus(7, ChronoUnit.DAYS);
        Instant monthStart = now.minus(30, ChronoUnit.DAYS);

        return EmailStatsDto.builder()
                .totalToday(emailLogRepository.countByTenantIdAndSentAtAfter(tenantId, todayStart))
                .totalWeek(emailLogRepository.countByTenantIdAndSentAtAfter(tenantId, weekStart))
                .totalMonth(emailLogRepository.countByTenantIdAndSentAtAfter(tenantId, monthStart))
                .confirmationsMonth(emailLogRepository.countByTenantIdAndTypeAndSentAtAfter(tenantId, EmailType.CONFIRMATION, monthStart))
                .remindersMonth(emailLogRepository.countByTenantIdAndTypeAndSentAtAfter(tenantId, EmailType.REMINDER, monthStart))
                .aftercareMonth(emailLogRepository.countByTenantIdAndTypeAndSentAtAfter(tenantId, EmailType.AFTERCARE, monthStart))
                .manualMonth(emailLogRepository.countByTenantIdAndTypeAndSentAtAfter(tenantId, EmailType.MANUAL, monthStart))
                .build();
    }

    private void sendAndLog(UUID tenantId, String to, String recipientName, String subject,
                            String html, EmailType type, UUID appointmentId) {
        EmailLog.EmailLogBuilder logBuilder = EmailLog.builder()
                .tenantId(tenantId)
                .recipientEmail(to)
                .recipientName(recipientName)
                .subject(subject)
                .type(type)
                .appointmentId(appointmentId)
                .sentAt(Instant.now());

        try {
            resendClient.send(to, subject, html);
            logBuilder.status(EmailStatus.SENT);
        } catch (Exception e) {
            logBuilder.status(EmailStatus.FAILED).errorMessage(e.getMessage());
            log.error("Email send failed for tenant {}: {}", tenantId, e.getMessage());
        }

        emailLogRepository.save(logBuilder.build());
    }

    private EmailLogDto toDto(EmailLog log) {
        return EmailLogDto.builder()
                .id(log.getId())
                .recipientEmail(log.getRecipientEmail())
                .recipientName(log.getRecipientName())
                .subject(log.getSubject())
                .type(log.getType())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .sentAt(log.getSentAt())
                .build();
    }
}
