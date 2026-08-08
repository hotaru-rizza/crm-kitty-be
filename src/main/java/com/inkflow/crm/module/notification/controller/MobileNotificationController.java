package com.inkflow.crm.module.notification.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.notification.dto.StaffNotificationDto;
import com.inkflow.crm.module.notification.dto.UnreadCountDto;
import com.inkflow.crm.module.notification.entity.Notification;
import com.inkflow.crm.module.notification.mapper.StaffNotificationMapper;
import com.inkflow.crm.module.notification.service.NotificationService;
import com.inkflow.crm.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/mobile/notifications")
@RequiredArgsConstructor
@Tag(name = "CRM · Mobile Notifications")
public class MobileNotificationController {

    private final NotificationService notificationService;
    private final StaffNotificationMapper staffNotificationMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffNotificationDto>>> list(PageRequest pageRequest) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Page<Notification> page = notificationService.getForUser(
                userId,
                pageRequest.getPage(),
                pageRequest.getSize()
        );

        List<StaffNotificationDto> data = page.getContent().stream()
                .map(staffNotificationMapper::toDto)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                data,
                PaginationDto.from(page)
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountDto>> unreadCount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(new UnreadCountDto(count)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);

        log.info("Notification marked read via API: notificationId={}", id);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        UUID userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllRead(userId);

        log.info("All notifications marked read via API: userId={}", userId);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
