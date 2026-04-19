package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ResendEmailClient resendClient;
    private final EmailLogRepository emailLogRepository;
    private final TenantRepository tenantRepository;

    public void sendConfirmation(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        if (email == null || email.isBlank()) return;

        Tenant tenant = tenantRepository.findById(appointment.getTenantId()).orElse(null);
        String studioName = tenant != null ? tenant.getName() : "INKAT";
        String timezone = tenant != null ? tenant.getTimezone() : "Europe/Kyiv";

        String html = EmailTemplates.confirmation(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                appointment.getStartTime(),
                timezone,
                studioName
        );

        sendAndLog(
                appointment.getTenantId(),
                email,
                appointment.getClient().getFullName(),
                "Запис підтверджено — " + studioName,
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

        String html = EmailTemplates.reminder(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                appointment.getArtist().getFirstName() + " " + appointment.getArtist().getLastName(),
                appointment.getStartTime(),
                timezone,
                studioName,
                hoursBefore
        );

        sendAndLog(
                appointment.getTenantId(),
                email,
                appointment.getClient().getFullName(),
                "Нагадування про запис — " + studioName,
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

        String html = EmailTemplates.aftercare(
                appointment.getClient().getFirstName(),
                appointment.getService().getTitle(),
                studioName
        );

        sendAndLog(
                appointment.getTenantId(),
                email,
                appointment.getClient().getFullName(),
                "Догляд після сеансу — " + studioName,
                html,
                EmailType.AFTERCARE,
                appointment.getId()
        );
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
