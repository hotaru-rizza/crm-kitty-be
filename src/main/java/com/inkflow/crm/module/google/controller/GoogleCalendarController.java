package com.inkflow.crm.module.google.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.google.dto.GoogleAuthUrlResponse;
import com.inkflow.crm.module.google.dto.GoogleCalendarStatusResponse;
import com.inkflow.crm.module.google.service.GoogleCalendarSyncService;
import com.inkflow.crm.module.staff.service.StaffLookup;
import com.inkflow.crm.security.RequirePermission;
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
    private final StaffLookup staffLookup;

    @GetMapping("/staff/{id}/google/auth-url")
    @RequirePermission({Permission.SETTINGS_ACCESS, Permission.STAFF_EDIT})
    public ResponseEntity<ApiResponse<GoogleAuthUrlResponse>> getAuthUrl(@PathVariable UUID id) {
        if (!properties.isConfigured()) {
            return ResponseEntity.badRequest().build();
        }
        staffLookup.requireStaff(id);
        String url = syncService.buildAuthorizationUrl(id.toString());

        return ResponseEntity.ok(ApiResponse.success(new GoogleAuthUrlResponse(url)));
    }

    @GetMapping("/public/google/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        try {
            syncService.handleCallback(code, state);
            log.info("Google Calendar connected via API: state={}", state);

            return ResponseEntity.status(302)
                    .header("Location", properties.getFrontendRedirect() + "?google=connected")
                    .build();
        } catch (Exception e) {
            log.error("Google OAuth callback failed: {}", e.getMessage());
            return ResponseEntity.status(302)
                    .header("Location", properties.getFrontendRedirect() + "?google=error")
                    .build();
        }
    }

    @GetMapping("/staff/{id}/google/status")
    @RequirePermission({Permission.SETTINGS_ACCESS, Permission.STAFF_VIEW, Permission.STAFF_EDIT})
    public ResponseEntity<ApiResponse<GoogleCalendarStatusResponse>> getStatus(@PathVariable UUID id) {
        Staff staff = staffLookup.requireStaff(id);
        boolean connected = staff.isGoogleCalendarConnected();
        String email = connected ? staff.getGoogleCalendarEmail() : null;
        boolean configured = properties.isConfigured();

        return ResponseEntity.ok(ApiResponse.success(new GoogleCalendarStatusResponse(connected, email, configured)));
    }

    @DeleteMapping("/staff/{id}/google/disconnect")
    @RequirePermission({Permission.SETTINGS_ACCESS, Permission.STAFF_EDIT})
    public ResponseEntity<ApiResponse<Void>> disconnect(@PathVariable UUID id) {
        syncService.disconnect(id);
        log.info("Google Calendar disconnected via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
