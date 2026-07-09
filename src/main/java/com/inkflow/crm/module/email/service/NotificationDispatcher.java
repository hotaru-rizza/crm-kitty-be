package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.module.email.dto.NotificationDispatchContext;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final TemplateEmailRenderer templateEmailRenderer;

    @Transactional
    public int enqueue(TriggerType triggerType, NotificationDispatchContext context) {
        if (isBlankEmail(context.recipientEmail())) {
            log.debug("Skipping enqueue for trigger {} tenant {}: no recipient email",
                    triggerType, context.tenantId());
            return 0;
        }

        List<EmailTemplate> templates = emailTemplateRepository
                .findByTriggerTypeAndEnabledTrue(triggerType);

        int enqueued = 0;
        for (EmailTemplate template : templates) {
            if (enqueueTemplate(template, triggerType, context)) {
                enqueued++;
            }
        }
        return enqueued;
    }

    @Transactional
    public EmailMessage enqueueManual(
            UUID tenantId,
            TriggerType triggerType,
            String recipientEmail,
            String recipientName,
            String subject,
            String body,
            UUID entityId) {

        EmailMessage message = EmailMessage.builder()
                .tenantId(tenantId)
                .triggerType(triggerType)
                .recipientEmail(recipientEmail)
                .recipientName(recipientName)
                .subject(subject)
                .body(body)
                .status(EmailMessageStatus.PENDING)
                .entityId(entityId)
                .createdAt(Instant.now())
                .build();

        return emailMessageRepository.save(message);
    }

    private boolean enqueueTemplate(EmailTemplate template, TriggerType triggerType, NotificationDispatchContext context) {
        RenderedEmail rendered = templateEmailRenderer.render(
                template, context.variables(), context.studioName(), context.studioLogoUrl());

        String dedupeKey = EmailDedupeKeys.forEnqueue(
                context.tenantId(), triggerType, context.entityId(), template.getId());
        if (dedupeKey != null && emailMessageRepository.existsByDedupeKey(dedupeKey)) {
            log.debug("Skipping duplicate enqueue: dedupeKey={}", dedupeKey);
            return false;
        }

        EmailMessage message = EmailMessage.builder()
                .tenantId(context.tenantId())
                .templateId(template.getId())
                .triggerType(triggerType)
                .recipientEmail(context.recipientEmail())
                .recipientName(context.recipientName())
                .subject(rendered.subject())
                .body(rendered.html())
                .status(EmailMessageStatus.PENDING)
                .entityId(context.entityId())
                .dedupeKey(dedupeKey)
                .createdAt(Instant.now())
                .build();

        try {
            emailMessageRepository.save(message);
            return true;
        } catch (DataIntegrityViolationException exception) {
            log.debug("Duplicate enqueue prevented by constraint: dedupeKey={}", dedupeKey);
            return false;
        }
    }

    private boolean isBlankEmail(String email) {
        return email == null || email.isBlank();
    }
}
