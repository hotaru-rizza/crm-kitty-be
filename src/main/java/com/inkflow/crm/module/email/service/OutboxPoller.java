package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.email.service.sending.ResendEmailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final EmailMessageRepository emailMessageRepository;
    private final ResendEmailClient resendEmailClient;
    private final InkflowProperties properties;

    @Scheduled(fixedRateString = "${inkflow.email.outbox.fixed-rate-ms:30000}")
    @Transactional
    public void processOutbox() {
        InkflowProperties.Outbox outbox = properties.getEmail().getOutbox();
        List<EmailMessage> pending = emailMessageRepository.findPendingForProcessing(
                Instant.now(), outbox.getBatchSize());

        for (EmailMessage message : pending) {
            deliver(message, outbox);
        }
    }

    private void deliver(EmailMessage message, InkflowProperties.Outbox outbox) {
        try {
            resendEmailClient.send(message.getRecipientEmail(), message.getSubject(), message.getBody());
            message.setStatus(EmailMessageStatus.SENT);
            message.setSentAt(Instant.now());
            message.setLastError(null);
            log.info("Outbox delivered: id={} tenant={} to={}",
                    message.getId(), message.getTenantId(), message.getRecipientEmail());
        } catch (Exception exception) {
            int attempts = message.getAttempts() + 1;
            message.setAttempts(attempts);
            message.setLastError(truncate(exception.getMessage(), 512));

            if (attempts >= outbox.getMaxAttempts()) {
                message.setStatus(EmailMessageStatus.FAILED);
                log.error("Outbox delivery failed permanently: id={} attempts={} error={}",
                        message.getId(), attempts, exception.getMessage());
            } else {
                message.setNextAttemptAt(Instant.now().plusMillis(outbox.backoffMsForAttempt(attempts)));
                log.warn("Outbox delivery failed, will retry: id={} attempt={} error={}",
                        message.getId(), attempts, exception.getMessage());
            }
        }

        emailMessageRepository.save(message);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
