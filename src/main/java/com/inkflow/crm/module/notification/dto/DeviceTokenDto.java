package com.inkflow.crm.module.notification.dto;

import com.inkflow.crm.module.notification.entity.DeviceToken;

import java.time.Instant;
import java.util.UUID;

public record DeviceTokenDto(
        UUID id,
        DeviceToken.Platform platform,
        String appVersion,
        Instant createdAt,
        Instant lastUsedAt
) {
}
