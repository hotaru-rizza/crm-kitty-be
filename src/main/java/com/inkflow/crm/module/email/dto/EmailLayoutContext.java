package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TemplateCategory;

public record EmailLayoutContext(
        String appName,
        String title,
        String bodyHtml,
        TemplateCategory category,
        String studioName,
        String studioLogoUrl,
        String actionUrl,
        String actionLabel) {}
