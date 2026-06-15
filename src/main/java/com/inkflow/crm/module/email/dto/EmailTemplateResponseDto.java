package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TriggerType;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record EmailTemplateResponseDto(
        UUID id,
        TriggerType triggerType,
        Integer offsetMinutes,
        String subject,
        String body,
        Boolean enabled,
        Boolean deletable,
        String builtinKey,
        TemplateCategory category,
        List<String> availableVars,
        Instant updatedAt
) {}
