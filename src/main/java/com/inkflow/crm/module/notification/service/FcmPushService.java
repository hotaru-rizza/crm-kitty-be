package com.inkflow.crm.module.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final ObjectMapper objectMapper;

    @Value("${fcm.server-key:}")
    private String fcmServerKey;

    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";
    private final RestClient restClient = RestClient.create();

    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        if (fcmServerKey == null || fcmServerKey.isBlank()) {
            log.debug("FCM disabled (no server key), skipping push for user {}", userId);
            return;
        }

        List<DeviceToken> tokens = deviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No device tokens for user {}, skipping push", userId);
            return;
        }

        for (DeviceToken deviceToken : tokens) {
            sendPush(deviceToken.getToken(), title, body, data);
        }
    }

    private void sendPush(String token, String title, String body, Map<String, String> data) {
        try {
            Map<String, Object> notification = Map.of(
                    "title", title,
                    "body", body,
                    "sound", "default"
            );

            Map<String, Object> payload = Map.of(
                    "to", token,
                    "notification", notification,
                    "data", data != null ? data : Map.of(),
                    "priority", "high"
            );

            String json = objectMapper.writeValueAsString(payload);

            restClient.post()
                    .uri(FCM_URL)
                    .header("Authorization", "key=" + fcmServerKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            log.debug("Push sent to token {}...", token.substring(0, Math.min(20, token.length())));

        } catch (Exception e) {
            log.warn("Failed to send FCM push: {}", e.getMessage());
        }
    }
}
