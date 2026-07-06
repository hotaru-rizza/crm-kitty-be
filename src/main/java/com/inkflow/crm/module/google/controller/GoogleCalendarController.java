package com.inkflow.crm.module.google.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.google.dto.GoogleAuthUrlResponse;
import com.inkflow.crm.module.google.dto.GoogleCalendarStatusResponse;
import com.inkflow.crm.module.google.service.GoogleCalendarAccessGuard;
import com.inkflow.crm.module.google.service.GoogleCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "CRM · Integrations")
public class GoogleCalendarController {

    private final GoogleCalendarSyncService syncService;
    private final GoogleCalendarProperties properties;
    private final GoogleCalendarAccessGuard accessGuard;

    @GetMapping("/staff/{id}/google/auth-url")
    public ResponseEntity<ApiResponse<GoogleAuthUrlResponse>> getAuthUrl(@PathVariable UUID id) {
        if (!properties.isConfigured()) {
            return ResponseEntity.badRequest().build();
        }
        accessGuard.requireManageAccess(id);
        String url = syncService.buildAuthorizationUrl(id.toString());

        return ResponseEntity.ok(ApiResponse.success(new GoogleAuthUrlResponse(url)));
    }

    @GetMapping("/public/google/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        try {
            UUID staffId = syncService.handleCallback(code, state);
            log.info("Google Calendar connected via API: staffId={}", staffId);

            return ResponseEntity.status(302)
                    .header("Location", buildFrontendRedirect("connected", staffId))
                    .build();
        } catch (Exception exception) {
            log.error("Google OAuth callback failed: {}", exception.getMessage());
            return ResponseEntity.status(302)
                    .header("Location", buildFrontendRedirect("error", null))
                    .build();
        }
    }

    @GetMapping("/staff/{id}/google/status")
    public ResponseEntity<ApiResponse<GoogleCalendarStatusResponse>> getStatus(@PathVariable UUID id) {
        Staff staff = accessGuard.requireViewAccess(id);
        boolean connected = staff.isGoogleCalendarConnected();
        String email = connected ? staff.getGoogleCalendarEmail() : null;
        boolean configured = properties.isConfigured();

        return ResponseEntity.ok(ApiResponse.success(new GoogleCalendarStatusResponse(connected, email, configured)));
    }

    @DeleteMapping("/staff/{id}/google/disconnect")
    public ResponseEntity<ApiResponse<Void>> disconnect(@PathVariable UUID id) {
        accessGuard.requireManageAccess(id);
        syncService.disconnect(id);
        log.info("Google Calendar disconnected via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    private String buildFrontendRedirect(String status, UUID staffId) {
        String base = properties.getFrontendRedirect();
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? '&' : '?');
        url.append("google=").append(status);
        if (staffId != null) {
            url.append("&tab=account");
        }
        return url.toString();
    }
}
