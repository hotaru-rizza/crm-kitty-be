package com.inkflow.crm.module.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.notification.entity.Notification;
import com.inkflow.crm.module.notification.entity.NotificationChannel;
import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.repository.NotificationRepository;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FcmPushService fcmPushService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Notification send(UUID tenantId, UUID recipientId, NotificationType type,
                             String title, String body, Map<String, String> data) {
        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .recipientId(recipientId)
                .channel(NotificationChannel.PUSH)
                .type(type)
                .title(title)
                .body(body)
                .data(serializeData(data))
                .build();

        notification = notificationRepository.save(notification);

        try {
            fcmPushService.sendToUser(recipientId, title, body, data);
            notification.markAsSent();
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Push delivery failed for notification {}: {}", notification.getId(), e.getMessage());
        }

        return notification;
    }

    @Transactional
    public Notification createInApp(UUID tenantId, UUID recipientId, NotificationType type,
                                    String title, String body) {
        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .recipientId(recipientId)
                .channel(NotificationChannel.IN_APP)
                .type(type)
                .title(title)
                .body(body)
                .build();

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getForUser(UUID userId, int page, int size) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        notificationRepository.findByIdAndRecipientId(notificationId, userId).ifPresent(n -> {
            n.markAsRead();
            notificationRepository.save(n);
        });
    }

    private String serializeData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize notification data as JSON: {}", e.getMessage());
            return null;
        }
    }
}
