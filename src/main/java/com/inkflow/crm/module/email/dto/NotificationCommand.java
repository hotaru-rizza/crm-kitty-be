package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.module.email.enums.TemplateKey;

import java.util.Map;
import java.util.UUID;

public record NotificationCommand(
        UUID tenantId,
        TemplateKey templateKey,
        EmailRecipient recipient,
        Map<String, String> variables,
        UUID entityId,
        String studioName) {

    public static NotificationCommand forTenant(
            UUID tenantId,
            EmailRecipient recipient,
            TemplateKey templateKey,
            Map<String, String> variables,
            UUID entityId,
            EmailTenantContext context) {

        return new NotificationCommand(
                tenantId,
                templateKey,
                recipient,
                variables,
                entityId,
                context.studioName()
        );
    }
}
