package com.inkflow.crm.module.settings.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.settings.dto.*;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.module.settings.service.SettingsService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final RolePermissionService rolePermissionService;

    @GetMapping({"", "/company"})
    @RequirePermission("settings.access")
    public ResponseEntity<ApiResponse<CompanySettingsDto>> getCompanySettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getCompanySettings()));
    }

    @PatchMapping({"", "/company"})
    public ResponseEntity<ApiResponse<CompanySettingsDto>> updateCompanySettings(
            @Valid @RequestBody UpdateCompanySettingsRequest request) {
        CompanySettingsDto settings = settingsService.updateCompanySettings(request);
        log.info("Company settings updated via API");

        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/permissions")
    @RequirePermission("settings.roles")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(rolePermissionService.getAllPermissions()));
    }

    @GetMapping("/roles")
    @RequirePermission("settings.roles")
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
