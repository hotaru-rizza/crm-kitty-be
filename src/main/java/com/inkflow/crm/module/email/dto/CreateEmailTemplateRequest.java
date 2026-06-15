package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmailTemplateRequest(
        @NotNull TriggerType triggerType,
        Integer offsetMinutes,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String body,
        Boolean enabled
) {}
