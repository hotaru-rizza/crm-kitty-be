package com.inkflow.crm.module.notification.service;

import com.inkflow.crm.module.notification.dto.DeviceTokenDto;
import com.inkflow.crm.module.notification.dto.RegisterDeviceRequest;
import com.inkflow.crm.module.notification.dto.UnregisterDeviceRequest;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.mapper.DeviceTokenMapper;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import com.inkflow.crm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private DeviceTokenMapper deviceTokenMapper;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    @Test
    void register_createsNewTokenWhenMissing() {
        UUID userId = UUID.randomUUID();
        RegisterDeviceRequest request = new RegisterDeviceRequest(
                "token-abc", DeviceToken.Platform.ANDROID, "1.0.0");
        DeviceToken saved = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token(request.token())
                .platform(request.platform())
                .appVersion(request.appVersion())
                .build();
        DeviceTokenDto dto = new DeviceTokenDto(
                saved.getId(), saved.getPlatform(), saved.getAppVersion(), null, null);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(deviceTokenRepository.findByToken(request.token())).thenReturn(Optional.empty());
            when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(saved);
            when(deviceTokenMapper.toDto(saved)).thenReturn(dto);

            DeviceTokenDto result = deviceTokenService.register(request);

            assertEquals(dto, result);
            ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
            verify(deviceTokenRepository).save(captor.capture());
            assertEquals(userId, captor.getValue().getUserId());
            assertEquals(DeviceToken.Platform.ANDROID, captor.getValue().getPlatform());
        }
    }

    @Test
    void register_updatesExistingTokenOwner() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .token("token-abc")
                .platform(DeviceToken.Platform.IOS)
                .build();
        RegisterDeviceRequest request = new RegisterDeviceRequest(
                "token-abc", DeviceToken.Platform.ANDROID, "1.1.0");
        DeviceTokenDto dto = new DeviceTokenDto(
                existing.getId(), DeviceToken.Platform.ANDROID, "1.1.0", null, null);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(deviceTokenRepository.findByToken(request.token())).thenReturn(Optional.of(existing));
            when(deviceTokenRepository.save(existing)).thenReturn(existing);
            when(deviceTokenMapper.toDto(existing)).thenReturn(dto);

            deviceTokenService.register(request);

            assertEquals(userId, existing.getUserId());
            assertEquals(DeviceToken.Platform.ANDROID, existing.getPlatform());
            assertEquals("1.1.0", existing.getAppVersion());
            verify(deviceTokenRepository).save(existing);
        }
    }

    @Test
    void register_retriesAsUpdateWhenInsertRacesDuplicateToken() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .token("token-abc")
                .platform(DeviceToken.Platform.ANDROID)
                .build();
        RegisterDeviceRequest request = new RegisterDeviceRequest(
                "token-abc", DeviceToken.Platform.IOS, "2.0.0");
        DeviceTokenDto dto = new DeviceTokenDto(
                existing.getId(), DeviceToken.Platform.IOS, "2.0.0", null, null);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(deviceTokenRepository.findByToken(request.token()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existing));
            when(deviceTokenRepository.save(any(DeviceToken.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"))
                    .thenReturn(existing);
            when(deviceTokenMapper.toDto(existing)).thenReturn(dto);

            DeviceTokenDto result = deviceTokenService.register(request);

            assertEquals(dto, result);
            assertEquals(userId, existing.getUserId());
            assertEquals(DeviceToken.Platform.IOS, existing.getPlatform());
            assertEquals("2.0.0", existing.getAppVersion());
            verify(deviceTokenRepository, times(2)).save(any(DeviceToken.class));
        }
    }

    @Test
    void register_rethrowsWhenDuplicateInsertHasNoMatchingRow() {
        UUID userId = UUID.randomUUID();
        RegisterDeviceRequest request = new RegisterDeviceRequest(
                "token-abc", DeviceToken.Platform.ANDROID, "1.0.0");
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("duplicate");

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(deviceTokenRepository.findByToken(request.token())).thenReturn(Optional.empty());
            when(deviceTokenRepository.save(any(DeviceToken.class))).thenThrow(duplicate);

            assertThrows(DataIntegrityViolationException.class, () -> deviceTokenService.register(request));
        }
    }

    @Test
    void unregister_deletesOnlyOwnToken() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token("token-abc")
                .platform(DeviceToken.Platform.ANDROID)
                .build();

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

            deviceTokenService.unregister(new UnregisterDeviceRequest("token-abc"));

            verify(deviceTokenRepository).delete(existing);
        }
    }

    @Test
    void unregister_skipsForeignToken() {
        UUID userId = UUID.randomUUID();
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .token("token-abc")
                .platform(DeviceToken.Platform.ANDROID)
                .build();

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(deviceTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(existing));

            deviceTokenService.unregister(new UnregisterDeviceRequest("token-abc"));

            verify(deviceTokenRepository, never()).delete(any());
        }
    }
}
