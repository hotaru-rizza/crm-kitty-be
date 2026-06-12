package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateKey;
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
    private final EmailLogRepository emailLogRepository;

    public void send(NotificationCommand command) {
        if (isBlankEmail(command.recipient().email())) {
            log.debug("Skipping {} for tenant {}: no recipient email", command.templateKey(), command.tenantId());
            return;
        }

        RenderedEmail email = contentRenderer.render(command);

        deliver(command, email);
    }

    public boolean wasAlreadySent(TemplateKey templateKey, UUID entityId) {
        if (entityId == null) {
            return false;
        }

        return emailLogRepository.existsByTemplateKeyAndEntityId(templateKey.name(), entityId);
    }

    private void deliver(NotificationCommand command, RenderedEmail email) {
        EmailLog logEntry = buildLogEntry(command, email);

        try {
            resendClient.send(command.recipient().email(), email.subject(), email.html());
            logEntry.setStatus(EmailStatus.SENT);
            log.info("Email sent: key={} tenant={} to={}",
                    command.templateKey(), command.tenantId(), command.recipient().email());
        } catch (Exception exception) {
            logEntry.setStatus(EmailStatus.FAILED);
            logEntry.setErrorMessage(exception.getMessage());
            log.error("Email failed: key={} tenant={} to={} error={}",
                    command.templateKey(), command.tenantId(), command.recipient().email(), exception.getMessage());
        }

        emailLogRepository.save(logEntry);
    }

    private EmailLog buildLogEntry(NotificationCommand command, RenderedEmail email) {
        return EmailLog.builder()
                .tenantId(command.tenantId())
                .recipientEmail(command.recipient().email())
                .recipientName(command.recipient().name())
                .subject(email.subject())
                .type(EmailType.MANUAL)
                .templateKey(command.templateKey().name())
                .entityId(command.entityId())
                .sentAt(Instant.now())
                .build();
    }

    private boolean isBlankEmail(String email) {
        return email == null || email.isBlank();
    }
}
