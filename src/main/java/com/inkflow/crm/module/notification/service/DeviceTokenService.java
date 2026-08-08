package com.inkflow.crm.module.notification.service;

import com.inkflow.crm.module.notification.dto.DeviceTokenDto;
import com.inkflow.crm.module.notification.dto.RegisterDeviceRequest;
import com.inkflow.crm.module.notification.dto.UnregisterDeviceRequest;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.mapper.DeviceTokenMapper;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenMapper deviceTokenMapper;

    @Transactional
    public DeviceTokenDto register(RegisterDeviceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Instant now = Instant.now();

        DeviceToken deviceToken = deviceTokenRepository.findByToken(request.token())
                .map(existing -> updateExisting(existing, userId, request, now))
                .orElseGet(() -> createNew(userId, request, now));

        DeviceToken saved = deviceTokenRepository.save(deviceToken);
        log.info("Device token registered: userId={} platform={} deviceId={}",
                userId, saved.getPlatform(), saved.getId());

        return deviceTokenMapper.toDto(saved);
    }

    @Transactional
    public void unregister(UnregisterDeviceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        deviceTokenRepository.findByToken(request.token())
                .filter(token -> userId.equals(token.getUserId()))
                .ifPresentOrElse(
                        token -> {
                            deviceTokenRepository.delete(token);
                            log.info("Device token unregistered: userId={} deviceId={}", userId, token.getId());
                        },
                        () -> log.debug("Device token unregister skipped: no matching token for userId={}", userId)
                );
    }

    private DeviceToken updateExisting(
            DeviceToken existing,
            UUID userId,
            RegisterDeviceRequest request,
            Instant now) {
        existing.setUserId(userId);
        existing.setPlatform(request.platform());
        existing.setAppVersion(request.appVersion());
        existing.setLastUsedAt(now);
        return existing;
    }

    private DeviceToken createNew(UUID userId, RegisterDeviceRequest request, Instant now) {
        return DeviceToken.builder()
                .userId(userId)
                .token(request.token())
                .platform(request.platform())
                .appVersion(request.appVersion())
                .lastUsedAt(now)
                .build();
    }
}
