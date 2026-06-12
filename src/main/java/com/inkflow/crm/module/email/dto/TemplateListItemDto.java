package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TemplateCategory;
import lombok.Builder;

import java.util.List;

@Builder
public record TemplateListItemDto(
        String key,
        TemplateCategory category,
        String subject,
        String body,
        List<String> availableVars,
        boolean isOverridden
) {}
