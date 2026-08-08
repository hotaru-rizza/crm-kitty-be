package com.inkflow.crm.module.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.notification.entity.Notification;
import com.inkflow.crm.module.notification.entity.NotificationChannel;
import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.repository.NotificationRepository;
import com.inkflow.crm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmPushService fcmPushService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void stubObjectMapper() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"new_request\"}");
    }

    @Test
    void send_marksNotificationAsSentWhenPushSucceeds() {
        UUID tenantId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification saved = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .recipientId(recipientId)
                .channel(NotificationChannel.PUSH)
                .type(NotificationType.APPOINTMENT_REMINDER)
                .title("Reminder")
                .body("Tomorrow at 10:00")
                .build();

        when(notificationRepository.save(any())).thenReturn(saved);

        Notification result = notificationService.send(
                tenantId, recipientId, NotificationType.APPOINTMENT_REMINDER,
                "Reminder", "Tomorrow at 10:00", Map.of("appointmentId", "123"));

        assertNotNull(result);
        assertTrue(result.getIsSent());
        verify(fcmPushService).sendToUser(recipientId, "Reminder", "Tomorrow at 10:00", Map.of("appointmentId", "123"));
        verify(notificationRepository).save(saved);
    }

    @Test
    void send_keepsNotificationUnsentWhenPushFails() {
        UUID tenantId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification saved = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .recipientId(recipientId)
                .channel(NotificationChannel.PUSH)
                .type(NotificationType.APPOINTMENT_REMINDER)
                .title("Reminder")
                .body("Tomorrow at 10:00")
                .build();

        when(notificationRepository.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("fcm down"))
                .when(fcmPushService).sendToUser(any(), any(), any(), any());

        Notification result = notificationService.send(
                tenantId, recipientId, NotificationType.APPOINTMENT_REMINDER,
                "Reminder", "Tomorrow at 10:00", null);

        assertEquals(saved.getId(), result.getId());
        assertFalse(result.getIsSent());
    }

    @Test
    void createInApp_persistsNotification() {
        UUID tenantId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification saved = Notification.builder().id(UUID.randomUUID()).build();

        when(notificationRepository.save(any())).thenReturn(saved);

        Notification result = notificationService.createInApp(
                tenantId, recipientId, NotificationType.NEW_REQUEST, "New request", "Client asked for quote");

        assertEquals(saved.getId(), result.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getForUser_returnsPagedNotifications() {
        UUID userId = UUID.randomUUID();
        Notification notification = Notification.builder().id(UUID.randomUUID()).build();
        Page<Notification> page = new PageImpl<>(List.of(notification));

        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<Notification> result = notificationService.getForUser(userId, 0, 20);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void markAsRead_updatesExistingNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = Notification.builder().id(notificationId).isRead(false).build();

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(notificationRepository.findByIdAndRecipientId(notificationId, userId))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(notification)).thenReturn(notification);

            notificationService.markAsRead(notificationId);

            assertTrue(notification.getIsRead());
            verify(notificationRepository).save(notification);
        }
    }

    @Test
    void shouldPersistDataStringWhenSendIncludesPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Map<String, String> data = Map.of("requestId", "req-1", "type", "new_request");
        Notification saved = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .recipientId(recipientId)
                .channel(NotificationChannel.PUSH)
                .type(NotificationType.NEW_REQUEST)
                .title("New request")
                .body("Client asked")
                .data(data.toString())
                .build();

        when(notificationRepository.save(any())).thenReturn(saved);

        Notification result = notificationService.send(
                tenantId, recipientId, NotificationType.NEW_REQUEST,
                "New request", "Client asked", data);

        assertEquals(data.toString(), result.getData());
        assertTrue(result.getIsSent());
    }

    @Test
    void markAsRead_doesNothingWhenNotificationMissing() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(notificationRepository.findByIdAndRecipientId(notificationId, userId)).thenReturn(Optional.empty());

            notificationService.markAsRead(notificationId);

            verify(notificationRepository, never()).save(any());
        }
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByRecipientIdAndIsReadFalse(userId)).thenReturn(3L);

        assertEquals(3L, notificationService.getUnreadCount(userId));
    }

    @Test
    void markAllRead_delegatesToRepository() {
        UUID userId = UUID.randomUUID();

        notificationService.markAllRead(userId);

        verify(notificationRepository).markAllAsRead(userId);
    }
}
