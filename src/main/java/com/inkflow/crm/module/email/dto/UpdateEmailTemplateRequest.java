package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TriggerType;
import jakarta.validation.constraints.Size;

public record UpdateEmailTemplateRequest(
        TriggerType triggerType,
        Integer offsetMinutes,
        @Size(max = 255) String subject,
        String body,
        Boolean enabled
) {}
