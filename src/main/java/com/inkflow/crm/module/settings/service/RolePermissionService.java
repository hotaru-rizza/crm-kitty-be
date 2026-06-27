package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.domain.entity.RolePermission;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.RolePermissionRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.settings.dto.PermissionDto;
import com.inkflow.crm.module.settings.dto.RolePermissionsDto;
import com.inkflow.crm.module.settings.dto.UpdateRolePermissionsRequest;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return Arrays.stream(Permission.values())
                .map(this::toPermissionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RolePermissionsDto> getAllRolePermissions() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        initializeDefaultPermissionsIfNeeded(tenantId);

        return Arrays.stream(UserRole.values())
                .map(role -> toRolePermissionsDto(tenantId, role))
                .toList();
    }

    @Transactional
    public RolePermissionsDto updateRolePermissions(String roleValue, UpdateRolePermissionsRequest request) {
        SecurityUtils.requireOwner();

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = UserRole.fromValue(roleValue);

        rolePermissionRepository.deleteByTenantIdAndRole(tenantId, role);
        saveGrantedPermissions(tenantId, role, request.getPermissions());

        log.info("Role permissions updated: tenantId={} role={} count={}",
                tenantId, roleValue, request.getPermissions().size());

        auditRecorder.record(
                AuditAction.PERMISSIONS_CHANGE,
                AuditEntityType.ROLE,
                roleValue,
                roleValue,
                null,
                String.valueOf(request.getPermissions().size())
        );

        return RolePermissionsDto.builder()
                .role(roleValue)
                .permissions(request.getPermissions())
                .build();
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID tenantId, UserRole role, String permission) {
        if (role == UserRole.OWNER) {
            return true;
        }

        return rolePermissionRepository.findByTenantIdAndRoleAndPermission(tenantId, role, permission)
                .map(RolePermission::getGranted)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> getGrantedPermissions(UUID tenantId, UserRole role) {
        if (role == UserRole.OWNER) {
            return RolePermissionDefaults.allPermissionValues().stream().toList();
        }

        initializeDefaultPermissionsIfNeeded(tenantId);

        return rolePermissionRepository.findByTenantIdAndRole(tenantId, role).stream()
                .filter(RolePermission::getGranted)
                .map(RolePermission::getPermission)
                .toList();
    }

    private void initializeDefaultPermissionsIfNeeded(UUID tenantId) {
        if (!rolePermissionRepository.findByTenantId(tenantId).isEmpty()) {
            return;
        }

        seedRolePermissions(tenantId);
        log.info("Default role permissions seeded for tenantId={}", tenantId);
    }

    private void seedRolePermissions(UUID tenantId) {
        for (Permission permission : Permission.values()) {
            saveRolePermission(tenantId, UserRole.OWNER, permission.getValue(), true);
            saveRolePermission(tenantId, UserRole.ADMIN, permission.getValue(),
                    RolePermissionDefaults.isGrantedForAdmin(permission));
            saveRolePermission(tenantId, UserRole.ARTIST, permission.getValue(),
                    RolePermissionDefaults.isGrantedForArtist(permission));
        }
    }

    private void saveGrantedPermissions(UUID tenantId, UserRole role, List<String> permissions) {
        for (String permission : permissions) {
            saveRolePermission(tenantId, role, permission, true);
        }
    }

    private void saveRolePermission(UUID tenantId, UserRole role, String permission, boolean granted) {
        rolePermissionRepository.save(RolePermission.builder()
                .tenantId(tenantId)
                .role(role)
                .permission(permission)
                .granted(granted)
                .build());
    }

    private RolePermissionsDto toRolePermissionsDto(UUID tenantId, UserRole role) {
        List<String> permissions = rolePermissionRepository.findByTenantIdAndRole(tenantId, role).stream()
                .filter(RolePermission::getGranted)
                .map(RolePermission::getPermission)
                .toList();

        return RolePermissionsDto.builder()
                .role(role.getValue())
                .permissions(permissions)
                .build();
    }

    private PermissionDto toPermissionDto(Permission permission) {
        return PermissionDto.builder()
                .value(permission.getValue())
                .category(permission.getCategory())
                .description(permission.getDescription())
                .build();
    }
}
