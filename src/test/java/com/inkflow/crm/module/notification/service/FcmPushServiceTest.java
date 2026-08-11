package com.inkflow.crm.module.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.inkflow.crm.module.notification.config.FcmProperties;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private FcmProperties fcmProperties;

    @Mock
    private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FcmPushService fcmPushService;

    @Test
    void sendToUser_skipsWhenFcmDisabled() {
        UUID userId = UUID.randomUUID();
        when(fcmProperties.isEnabled()).thenReturn(false);

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of("key", "value"));

        verifyNoInteractions(deviceTokenRepository);
        verifyNoInteractions(firebaseMessagingProvider);
    }

    @Test
    void sendToUser_skipsWhenMessagingBeanMissing() {
        UUID userId = UUID.randomUUID();
        when(fcmProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(null);

        fcmPushService.sendToUser(userId, "Title", "Body", null);

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void sendToUser_skipsWhenNoDeviceTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        when(fcmProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of());

        fcmPushService.sendToUser(userId, "Title", "Body", null);

        verify(deviceTokenRepository).findByUserId(userId);
        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    @Test
    void sendToUser_sendsViaFirebaseMessaging() throws Exception {
        UUID userId = UUID.randomUUID();
        DeviceToken token = DeviceToken.builder()
                .userId(userId)
                .token("device-token-123")
                .platform(DeviceToken.Platform.ANDROID)
                .build();

        when(fcmProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of(token));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of("type", "new_request"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(messageCaptor.capture());
        assertNotNull(messageCaptor.getValue());
        verify(deviceTokenRepository).save(token);
    }

    @Test
    void sendToUser_deletesTokenWhenUnregistered() throws Exception {
        UUID userId = UUID.randomUUID();
        DeviceToken token = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token("stale-token")
                .platform(DeviceToken.Platform.IOS)
                .build();

        FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        when(fcmProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingProvider.getIfAvailable()).thenReturn(firebaseMessaging);
        when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of(token));
        when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

        fcmPushService.sendToUser(userId, "Title", "Body", Map.of());

        verify(deviceTokenRepository).delete(token);
        verify(deviceTokenRepository, never()).save(token);
    }
}
