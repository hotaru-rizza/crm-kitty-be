package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.EmailContentRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSender {

    private final EmailContentRenderer contentRenderer;
    private final ResendEmailClient resendClient;
    private final EmailMessageRepository emailMessageRepository;

    public void send(NotificationCommand command) {
        if (isBlankEmail(command.recipient().email())) {
            log.debug("Skipping {} for tenant {}: no recipient email", command.templateKey(), command.tenantId());
            return;
        }

        RenderedEmail renderedEmail = contentRenderer.render(command);
        deliver(command, renderedEmail);
    }

    public boolean wasAlreadySent(TemplateKey templateKey, UUID entityId) {
        if (entityId == null) {
            return false;
        }

        TriggerType triggerType = mapLegacyTemplateKey(templateKey);
        return emailMessageRepository.existsByTriggerTypeAndEntityIdAndStatus(
                triggerType, entityId, EmailMessageStatus.SENT);
    }

    private void deliver(NotificationCommand command, RenderedEmail renderedEmail) {
        EmailMessage logEntry = buildLogEntry(command, renderedEmail);

        try {
            resendClient.send(command.recipient().email(), renderedEmail.subject(), renderedEmail.html());
            logEntry.setStatus(EmailMessageStatus.SENT);
            logEntry.setSentAt(Instant.now());
            log.info("Email sent: key={} tenant={} to={}",
                    command.templateKey(), command.tenantId(), command.recipient().email());
        } catch (Exception exception) {
            logEntry.setStatus(EmailMessageStatus.FAILED);
            logEntry.setLastError(exception.getMessage());
            log.error("Email failed: key={} tenant={} to={} error={}",
                    command.templateKey(), command.tenantId(), command.recipient().email(), exception.getMessage());
        }

        emailMessageRepository.save(logEntry);
    }

    private EmailMessage buildLogEntry(NotificationCommand command, RenderedEmail email) {
        return EmailMessage.builder()
                .tenantId(command.tenantId())
                .triggerType(mapLegacyTemplateKey(command.templateKey()))
                .recipientEmail(command.recipient().email())
                .recipientName(command.recipient().name())
                .subject(email.subject())
                .body(email.html())
                .status(EmailMessageStatus.SENT)
                .entityId(command.entityId())
                .createdAt(Instant.now())
                .build();
    }

    private TriggerType mapLegacyTemplateKey(TemplateKey templateKey) {
        return switch (templateKey) {
            case BOOKING_CONFIRMED -> TriggerType.BOOKING_CONFIRMED;
            case BOOKING_CANCELED -> TriggerType.BOOKING_CANCELED;
            case BOOKING_RESCHEDULED -> TriggerType.BOOKING_RESCHEDULED;
            case BOOKING_REMINDER, PREP_INSTRUCTIONS -> TriggerType.BEFORE_BOOKING;
            case AFTERCARE_INSTRUCTIONS, REVIEW_REQUEST -> TriggerType.AFTER_BOOKING;
            case BIRTHDAY -> TriggerType.CLIENT_BIRTHDAY;
            case WINBACK -> TriggerType.CLIENT_INACTIVE;
            case BULK_EMAIL -> TriggerType.MANUAL;
            default -> TriggerType.MANUAL;
        };
    }

    private boolean isBlankEmail(String email) {
        return email == null || email.isBlank();
    }
}
