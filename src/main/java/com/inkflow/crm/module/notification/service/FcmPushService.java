package com.inkflow.crm.module.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.inkflow.crm.module.notification.config.FcmProperties;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmProperties fcmProperties;
    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;

    @Transactional
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        FirebaseMessaging messaging = resolveMessaging();
        if (messaging == null) {
            log.debug("FCM disabled, skipping push for user {}", userId);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No device tokens for user {}, skipping push", userId);
            return;
        }

        Map<String, String> payload = data != null ? data : Map.of();

        for (DeviceToken deviceToken : tokens) {
            sendToDevice(messaging, deviceToken, title, body, payload);
        }
    }

    private FirebaseMessaging resolveMessaging() {
        if (!fcmProperties.isEnabled()) {
            return null;
        }
        return firebaseMessaging.getIfAvailable();
    }

    private void sendToDevice(
            FirebaseMessaging messaging,
            DeviceToken deviceToken,
            String title,
            String body,
            Map<String, String> data) {
        Message message = Message.builder()
                .setToken(deviceToken.getToken())
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();

        try {
            messaging.send(message);
            deviceToken.setLastUsedAt(Instant.now());
            deviceTokenRepository.save(deviceToken);
            log.debug("FCM push sent for user {}", deviceToken.getUserId());
        } catch (FirebaseMessagingException e) {
            handleSendFailure(deviceToken, e);
        } catch (Exception e) {
            log.warn("Failed to send FCM push for user {}: {}", deviceToken.getUserId(), e.getMessage());
        }
    }

    private void handleSendFailure(DeviceToken deviceToken, FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            deviceTokenRepository.delete(deviceToken);
            log.info("Removed invalid FCM token for user {}: {}", deviceToken.getUserId(), errorCode);
            return;
        }

        log.warn("Failed to send FCM push for user {}: {} ({})",
                deviceToken.getUserId(), e.getMessage(), errorCode);
    }
}
