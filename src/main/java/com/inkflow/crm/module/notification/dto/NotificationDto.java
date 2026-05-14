package com.inkflow.crm.module.notification.dto;

import com.inkflow.crm.module.notification.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        boolean isRead,
        Instant createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                n.getIsRead(),
                n.getCreatedAt()
        );
    }
}
