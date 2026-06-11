package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.PreparedEmail;
import com.inkflow.crm.module.email.mapper.EmailLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ResendEmailClient resendClient;
    private final EmailLogRepository emailLogRepository;
    private final EmailTenantContextLoader tenantContextLoader;
    private final AppointmentEmailComposer appointmentEmailComposer;
    private final EmailLogMapper emailLogMapper;

    public void sendConfirmation(Appointment appointment) {
        sendToClient(appointment, "CONFIRMATION", EmailType.CONFIRMATION, (context, template) ->
                appointmentEmailComposer.confirmation(appointment, context, template));
    }

    public void sendReminder(Appointment appointment, int hoursBefore) {
        if (!hasClientEmail(appointment)) {
            return;
        }

        UUID tenantId = appointment.getTenantId();
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        Map<String, String> template = tenantContextLoader.loadTemplateEntry(tenantId, "REMINDER");
        PreparedEmail prepared = appointmentEmailComposer.reminder(appointment, context, template, hoursBefore);

        sendAndLog(tenantId, prepared, EmailType.REMINDER, appointment.getId());
    }

    public void sendAftercare(Appointment appointment) {
        sendToClient(appointment, "AFTERCARE", EmailType.AFTERCARE, (context, template) ->
                appointmentEmailComposer.aftercare(appointment, context, template));
    }

    public void sendCancellation(Appointment appointment) {
        sendToClient(appointment, "CANCELLATION", EmailType.CANCELLATION, (context, template) ->
                appointmentEmailComposer.cancellation(appointment, context, template));
    }

    public void sendReschedule(Appointment appointment) {
        sendToClient(appointment, "RESCHEDULE", EmailType.RESCHEDULE, (context, template) ->
                appointmentEmailComposer.reschedule(appointment, context, template));
    }

    public void sendStaffNewAppointment(Appointment appointment) {
        sendToStaff(appointment, EmailType.STAFF_NEW_APPOINTMENT,
                context -> appointmentEmailComposer.staffNewAppointment(appointment, context));
    }

    public void sendStaffCancellation(Appointment appointment) {
        sendToStaff(appointment, EmailType.STAFF_CANCELLATION,
                context -> appointmentEmailComposer.staffCancellation(appointment, context));
    }

    public void sendStaffReschedule(Appointment appointment) {
        sendToStaff(appointment, EmailType.STAFF_RESCHEDULE,
                context -> appointmentEmailComposer.staffReschedule(appointment, context));
    }

    public void sendManual(UUID tenantId, String recipientEmail, String recipientName, String subject, String textBody) {
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        String html = EmailTemplates.manual(subject, textBody, context.studioName());

        sendAndLog(
                tenantId,
                new PreparedEmail(recipientEmail, recipientName, subject, html),
                EmailType.MANUAL,
                null
        );
    }

    public boolean wasAlreadySent(UUID appointmentId, EmailType type) {
        return emailLogRepository.existsByAppointmentIdAndType(appointmentId, type);
    }

    @Transactional(readOnly = true)
    public Page<EmailLogDto> getLog(UUID tenantId, EmailType type, Instant from, Instant to, Pageable pageable) {
        return emailLogRepository.findFiltered(tenantId, type, from, to, pageable)
                .map(emailLogMapper::toDto);
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

    private void sendToClient(
            Appointment appointment,
            String templateType,
            EmailType emailType,
            EmailComposer composer) {
        if (!hasClientEmail(appointment)) {
            return;
        }

        UUID tenantId = appointment.getTenantId();
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        Map<String, String> template = tenantContextLoader.loadTemplateEntry(tenantId, templateType);
        PreparedEmail prepared = composer.compose(context, template);

        sendAndLog(tenantId, prepared, emailType, appointment.getId());
    }

    private void sendToStaff(
            Appointment appointment,
            EmailType emailType,
            StaffEmailComposer composer) {
        String email = appointment.getArtist().getEmail();
        if (email == null || email.isBlank()) {
            return;
        }

        UUID tenantId = appointment.getTenantId();
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        PreparedEmail prepared = composer.compose(context);

        sendAndLog(tenantId, prepared, emailType, appointment.getId());
    }

    private boolean hasClientEmail(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        return email != null && !email.isBlank();
    }

    private void sendAndLog(UUID tenantId, PreparedEmail prepared, EmailType type, UUID appointmentId) {
        EmailLog.EmailLogBuilder logBuilder = EmailLog.builder()
                .tenantId(tenantId)
                .recipientEmail(prepared.recipientEmail())
                .recipientName(prepared.recipientName())
                .subject(prepared.subject())
                .type(type)
                .appointmentId(appointmentId)
                .sentAt(Instant.now());

        try {
            resendClient.send(prepared.recipientEmail(), prepared.subject(), prepared.html());
            logBuilder.status(EmailStatus.SENT);
        } catch (Exception e) {
            logBuilder.status(EmailStatus.FAILED).errorMessage(e.getMessage());
            log.error("Email send failed for tenant {}: {}", tenantId, e.getMessage());
        }

        emailLogRepository.save(logBuilder.build());
    }

    @FunctionalInterface
    private interface EmailComposer {
        PreparedEmail compose(EmailTenantContext context, Map<String, String> template);
    }

    @FunctionalInterface
    private interface StaffEmailComposer {
        PreparedEmail compose(EmailTenantContext context);
    }
}
