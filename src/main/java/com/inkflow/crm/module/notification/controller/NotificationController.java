package com.inkflow.crm.module.notification.controller;

import com.inkflow.crm.module.notification.dto.NotificationDto;
import com.inkflow.crm.module.notification.dto.RegisterTokenRequest;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import com.inkflow.crm.module.notification.entity.Notification;
import com.inkflow.crm.module.notification.repository.DeviceTokenRepository;
import com.inkflow.crm.module.notification.service.NotificationService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final DeviceTokenRepository deviceTokenRepository;

    @GetMapping
    public ResponseEntity<Page<NotificationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Page<NotificationDto> notifications = notificationService.getForUser(userId, page, size)
                .map(NotificationDto::from);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead() {
        UUID userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/device-token")
    public ResponseEntity<Void> registerToken(@RequestBody RegisterTokenRequest body) {
        UUID userId = SecurityUtils.getCurrentUserId();

        deviceTokenRepository.findByToken(body.token()).ifPresentOrElse(
                existing -> {
                    existing.setUserId(userId);
                    existing.setLastUsedAt(Instant.now());
                    deviceTokenRepository.save(existing);
                },
                () -> deviceTokenRepository.save(DeviceToken.builder()
                        .userId(userId)
                        .token(body.token())
                        .platform(body.platform())
                        .build())
        );

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/device-token")
    public ResponseEntity<Void> unregisterToken(@RequestParam String token) {
        deviceTokenRepository.deleteByToken(token);
        return ResponseEntity.ok().build();
    }
}
