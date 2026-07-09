package com.inkflow.crm.module.email.dto;

import java.util.Map;
import java.util.UUID;

public record NotificationDispatchContext(
        UUID tenantId,
        String recipientEmail,
        String recipientName,
        UUID entityId,
        Map<String, String> variables,
        String studioName,
        String studioLogoUrl
) {
    public static NotificationDispatchContext of(
            UUID tenantId,
            String recipientEmail,
            String recipientName,
            UUID entityId,
            Map<String, String> variables,
            String studioName,
            String studioLogoUrl) {
        return new NotificationDispatchContext(
                tenantId, recipientEmail, recipientName, entityId, variables, studioName, studioLogoUrl);
    }
}
