package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.PreparedEmail;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.mapper.EmailLogMapper;
import com.inkflow.crm.module.email.template.EmailLayout;
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
    private final EmailTenantContextLoader tenantContextLoader;
    private final EmailLogMapper emailLogMapper;
    private final InkflowProperties inkflowProperties;

    public void sendManual(UUID tenantId, String recipientEmail, String recipientName, String subject, String textBody) {
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        String bodyHtml = EmailLayout.toHtml(textBody);

        EmailLayoutContext layout = new EmailLayoutContext(
                inkflowProperties.getAppName(),
                subject,
                bodyHtml,
                TemplateCategory.CLIENT_OP,
                context.studioName(),
                null,
                null
        );
        String html = EmailLayout.wrap(layout);

        sendAndLog(
                tenantId,
                new PreparedEmail(recipientEmail, recipientName, subject, html),
                EmailType.MANUAL,
                null
        );
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

}
