package com.inkflow.crm.module.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FcmPushService fcmPushService;

    @BeforeEach
    void disableFcmKey() {
        ReflectionTestUtils.setField(fcmPushService, "fcmServerKey", "");
    }

    @Test
    void sendToUser_skipsWhenFcmDisabled() {
        UUID userId = UUID.randomUUID();

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of("key", "value"));

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void sendToUser_skipsWhenNoDeviceTokens() {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(fcmPushService, "fcmServerKey", "test-server-key");

        org.mockito.Mockito.when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of());

        fcmPushService.sendToUser(userId, "Title", "Body", null);

        org.mockito.Mockito.verify(deviceTokenRepository).findByUserId(userId);
    }

    @Test
    void sendToUser_attemptsPushWhenTokenExists() throws Exception {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(fcmPushService, "fcmServerKey", "test-server-key");

        org.mockito.Mockito.when(deviceTokenRepository.findByUserId(userId))
                .thenReturn(List.of(DeviceToken.builder()
                        .userId(userId)
                        .token("device-token-123")
                        .platform(DeviceToken.Platform.WEB)
                        .build()));
        org.mockito.Mockito.when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenReturn("{}");

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of());

        org.mockito.Mockito.verify(deviceTokenRepository).findByUserId(userId);
        org.mockito.Mockito.verify(objectMapper).writeValueAsString(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendToUser_skipsWhenServerKeyIsNull() {
        ReflectionTestUtils.setField(fcmPushService, "fcmServerKey", null);
        UUID userId = UUID.randomUUID();

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of("key", "value"));

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void shouldSwallowExceptionWhenObjectMapperFails() throws Exception {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(fcmPushService, "fcmServerKey", "test-server-key");

        when(deviceTokenRepository.findByUserId(userId))
                .thenReturn(List.of(DeviceToken.builder()
                        .userId(userId)
                        .token("device-token-123")
                        .platform(DeviceToken.Platform.WEB)
                        .build()));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json error"));

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of("key", "value"));

        verify(deviceTokenRepository).findByUserId(userId);
        verify(objectMapper).writeValueAsString(any());
    }
}
