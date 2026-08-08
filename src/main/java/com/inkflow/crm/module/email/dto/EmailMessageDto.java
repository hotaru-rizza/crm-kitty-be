package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.module.email.enums.TriggerType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record EmailMessageDto(
        UUID id,
        UUID templateId,
        TriggerType triggerType,
        String recipientEmail,
        String recipientName,
        String subject,
        EmailMessageStatus status,
        String lastError,
        Integer attempts,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant sentAt
) {}
