package com.inkflow.crm.module.notification.service;

import com.inkflow.crm.module.notification.entity.Notification;
import com.inkflow.crm.module.notification.entity.NotificationChannel;
import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.repository.NotificationRepository;
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
                .data(data != null ? data.toString() : null)
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
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.markAsRead();
            notificationRepository.save(n);
        });
    }
}
