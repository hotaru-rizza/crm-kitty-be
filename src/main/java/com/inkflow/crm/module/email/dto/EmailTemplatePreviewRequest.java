package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmailTemplatePreviewRequest(
        @NotNull TriggerType triggerType,
        @NotBlank String subject,
        @NotBlank String body) {
}
