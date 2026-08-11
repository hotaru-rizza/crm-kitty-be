package com.inkflow.crm.module.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StaffNotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        Map<String, String> data,
        boolean read,
        boolean sent,
        Instant createdAt
) {
}
