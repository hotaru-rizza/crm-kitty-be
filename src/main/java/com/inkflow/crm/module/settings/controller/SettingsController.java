package com.inkflow.crm.module.settings.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.settings.dto.*;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.module.settings.service.SettingsService;
import com.inkflow.crm.module.settings.service.UserSettingsService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@Tag(name = "CRM · Settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final RolePermissionService rolePermissionService;
    private final UserSettingsService userSettingsService;

    @GetMapping({"", "/company"})
    @RequirePermission(Permission.SETTINGS_ACCESS)
    public ResponseEntity<ApiResponse<CompanySettingsDto>> getCompanySettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getCompanySettings()));
    }

    @PatchMapping("/company")
    @RequirePermission(Permission.SETTINGS_ACCESS)
    public ResponseEntity<ApiResponse<CompanySettingsDto>> updateCompanySettings(
            @RequestBody UpdateCompanySettingsRequest request) {
        CompanySettingsDto settings = settingsService.updateCompanySettings(request);
        log.info("Company settings updated via API");

        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/client-dormancy")
    @RequirePermission(Permission.SETTINGS_ACCESS)
    public ResponseEntity<ApiResponse<ClientDormancySettingsDto>> getClientDormancySettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getClientDormancySettings()));
    }

    @PutMapping("/client-dormancy")
    @RequirePermission(Permission.SETTINGS_ACCESS)
    public ResponseEntity<ApiResponse<ClientDormancySettingsDto>> updateClientDormancySettings(
            @Valid @RequestBody UpdateClientDormancySettingsRequest request) {
        ClientDormancySettingsDto settings = settingsService.updateClientDormancySettings(request);
        log.info("Client dormancy settings updated via API");
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserSettingsDto>> getUserSettings() {
        return ResponseEntity.ok(ApiResponse.success(userSettingsService.getCurrentUserSettings()));
    }

    @PatchMapping("/user")
    public ResponseEntity<ApiResponse<UserSettingsDto>> updateUserSettings(
            @RequestBody UpdateUserSettingsRequest request) {
        UserSettingsDto settings = userSettingsService.updateCurrentUserSettings(request);
        log.info("User settings updated via API");

        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/permissions")
    @RequirePermission(Permission.SETTINGS_ROLES)
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(rolePermissionService.getAllPermissions()));
    }

    @GetMapping("/roles")
    @RequirePermission(Permission.SETTINGS_ROLES)
    public ResponseEntity<ApiResponse<List<RolePermissionsDto>>> getAllRolePermissions() {
        return ResponseEntity.ok(ApiResponse.success(rolePermissionService.getAllRolePermissions()));
    }

    @PutMapping("/roles/{role}")
    public ResponseEntity<ApiResponse<RolePermissionsDto>> updateRolePermissions(
            @PathVariable String role,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        RolePermissionsDto updated = rolePermissionService.updateRolePermissions(role, request);
        log.info("Role permissions updated via API: role={}", role);

        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
