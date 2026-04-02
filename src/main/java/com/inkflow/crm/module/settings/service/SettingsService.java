package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.entity.RolePermission;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.RolePermissionRepository;
import com.inkflow.crm.module.settings.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final CompanySettingsRepository companySettingsRepository;
    private final TenantRepository tenantRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public CompanySettingsDto getCompanySettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultSettings(tenantId));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));
        return mapToDto(settings, tenant.getAccountType());
    }

    @Transactional
    public CompanySettingsDto updateCompanySettings(UpdateCompanySettingsRequest request) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultSettings(tenantId));

        if (request.getSmsReminders() != null) settings.setSmsReminders(request.getSmsReminders());
        if (request.getTelegramReminders() != null) settings.setTelegramReminders(request.getTelegramReminders());
        if (request.getEmailReminders() != null) settings.setEmailReminders(request.getEmailReminders());
        if (request.getReminderHoursBefore() != null) settings.setReminderHoursBefore(request.getReminderHoursBefore());
        if (request.getWorkingHoursStart() != null) settings.setWorkingHoursStart(LocalTime.parse(request.getWorkingHoursStart()));
        if (request.getWorkingHoursEnd() != null) settings.setWorkingHoursEnd(LocalTime.parse(request.getWorkingHoursEnd()));
        if (request.getAllowOnlineBooking() != null) settings.setAllowOnlineBooking(request.getAllowOnlineBooking());
        if (request.getMinAdvanceHours() != null) settings.setMinAdvanceHours(request.getMinAdvanceHours());
        if (request.getMaxAdvanceDays() != null) settings.setMaxAdvanceDays(request.getMaxAdvanceDays());

        settings.setUpdatedBy(SecurityUtils.getCurrentUserId());

        settings = companySettingsRepository.save(settings);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));
        return mapToDto(settings, tenant.getAccountType());
    }

    private CompanySettings createDefaultSettings(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));

        CompanySettings settings = CompanySettings.builder()
                .tenant(tenant)
                .smsReminders(false)
                .telegramReminders(false)
                .emailReminders(true)
                .reminderHoursBefore(24)
                .workingHoursStart(LocalTime.of(9, 0))
                .workingHoursEnd(LocalTime.of(22, 0))
                .allowOnlineBooking(true)
                .minAdvanceHours(24)
                .maxAdvanceDays(60)
                .build();

        return companySettingsRepository.save(settings);
    }

    private CompanySettingsDto mapToDto(CompanySettings settings, String accountType) {
        return CompanySettingsDto.builder()
                .smsReminders(settings.getSmsReminders())
                .telegramReminders(settings.getTelegramReminders())
                .emailReminders(settings.getEmailReminders())
                .reminderHoursBefore(settings.getReminderHoursBefore())
                .workingHoursStart(settings.getWorkingHoursStart().toString())
                .workingHoursEnd(settings.getWorkingHoursEnd().toString())
                .allowOnlineBooking(settings.getAllowOnlineBooking())
                .minAdvanceHours(settings.getMinAdvanceHours())
                .maxAdvanceDays(settings.getMaxAdvanceDays())
                .updatedAt(settings.getUpdatedAt())
                .accountType(accountType != null ? accountType : "STUDIO")
                .build();
    }

    // ========== PERMISSIONS ==========

    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return java.util.Arrays.stream(Permission.values())
                .map(p -> PermissionDto.builder()
                        .value(p.getValue())
                        .category(p.getCategory())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RolePermissionsDto> getAllRolePermissions() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        initializeDefaultPermissionsIfNeeded(tenantId);

        return java.util.Arrays.stream(UserRole.values())
                .map(role -> {
                    List<String> perms = rolePermissionRepository.findByTenantIdAndRole(tenantId, role)
                            .stream()
                            .filter(RolePermission::getGranted)
                            .map(RolePermission::getPermission)
                            .toList();
                    return RolePermissionsDto.builder()
                            .role(role.getValue())
                            .permissions(perms)
                            .build();
                })
                .toList();
    }

    @Transactional
    public RolePermissionsDto updateRolePermissions(String roleValue, UpdateRolePermissionsRequest request) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = UserRole.fromValue(roleValue);

        // Delete existing
        rolePermissionRepository.deleteByTenantIdAndRole(tenantId, role);

        // Create new
        for (String perm : request.getPermissions()) {
            RolePermission rp = RolePermission.builder()
                    .tenantId(tenantId)
                    .role(role)
                    .permission(perm)
                    .granted(true)
                    .build();
            rolePermissionRepository.save(rp);
        }

        return RolePermissionsDto.builder()
                .role(roleValue)
                .permissions(request.getPermissions())
                .build();
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID tenantId, UserRole role, String permission) {
        if (role == UserRole.OWNER) return true;
        return rolePermissionRepository.findByTenantIdAndRoleAndPermission(tenantId, role, permission)
                .map(RolePermission::getGranted)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> getGrantedPermissions(UUID tenantId, UserRole role) {
        if (role == UserRole.OWNER) {
            return java.util.Arrays.stream(Permission.values())
                    .map(Permission::getValue)
                    .toList();
        }
        initializeDefaultPermissionsIfNeeded(tenantId);
        return rolePermissionRepository.findByTenantIdAndRole(tenantId, role)
                .stream()
                .filter(RolePermission::getGranted)
                .map(RolePermission::getPermission)
                .toList();
    }

    private void initializeDefaultPermissionsIfNeeded(UUID tenantId) {
        if (!rolePermissionRepository.findByTenantId(tenantId).isEmpty()) return;

        // Owner gets all
        for (Permission p : Permission.values()) {
            rolePermissionRepository.save(RolePermission.builder()
                    .tenantId(tenantId).role(UserRole.OWNER).permission(p.getValue()).granted(true).build());
        }

        // Admin gets most
        for (Permission p : Permission.values()) {
            boolean granted = !p.getValue().equals("settings.roles");
            rolePermissionRepository.save(RolePermission.builder()
                    .tenantId(tenantId).role(UserRole.ADMIN).permission(p.getValue()).granted(granted).build());
        }

        // Artist gets limited
        List<String> artistPerms = List.of(
                "clients.view_own", "projects.view_own", "calendar.view_own",
                "calendar.create", "calendar.edit", "calendar.cancel"
        );
        for (Permission p : Permission.values()) {
            rolePermissionRepository.save(RolePermission.builder()
                    .tenantId(tenantId).role(UserRole.ARTIST).permission(p.getValue())
                    .granted(artistPerms.contains(p.getValue())).build());
        }
    }
}
