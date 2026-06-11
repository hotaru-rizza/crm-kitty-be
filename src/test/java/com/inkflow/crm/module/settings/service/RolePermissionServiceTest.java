package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.RolePermission;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.RolePermissionRepository;
import com.inkflow.crm.module.settings.dto.RolePermissionsDto;
import com.inkflow.crm.module.settings.dto.UpdateRolePermissionsRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllPermissions_returnsAllEnumValues() {
        assertEquals(Permission.values().length, rolePermissionService.getAllPermissions().size());
        assertTrue(rolePermissionService.getAllPermissions().stream()
                .anyMatch(dto -> Permission.CLIENTS_VIEW_ALL.getValue().equals(dto.getValue())));
    }

    @Test
    void hasPermission_ownerAlwaysGranted() {
        UUID tenantId = UUID.randomUUID();

        assertTrue(rolePermissionService.hasPermission(tenantId, UserRole.OWNER, Permission.REQUESTS_CREATE.getValue()));
    }

    @Test
    void hasPermission_returnsGrantedFlagForRole() {
        UUID tenantId = UUID.randomUUID();
        RolePermission granted = RolePermission.builder()
                .tenantId(tenantId)
                .role(UserRole.ARTIST)
                .permission(Permission.REQUESTS_CREATE.getValue())
                .granted(true)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRoleAndPermission(
                tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(Optional.of(granted));

        assertTrue(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()));
    }

    @Test
    void hasPermission_returnsFalseWhenMissing() {
        UUID tenantId = UUID.randomUUID();

        when(rolePermissionRepository.findByTenantIdAndRoleAndPermission(
                tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(Optional.empty());

        assertFalse(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()));
    }

    @Test
    void hasPermission_returnsFalseWhenExplicitlyDenied() {
        UUID tenantId = UUID.randomUUID();
        RolePermission denied = RolePermission.builder()
                .tenantId(tenantId)
                .role(UserRole.ARTIST)
                .permission(Permission.REQUESTS_CREATE.getValue())
                .granted(false)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRoleAndPermission(
                tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(Optional.of(denied));

        assertFalse(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()));
    }

    @Test
    void getGrantedPermissions_ownerReturnsAllPermissions() {
        UUID tenantId = UUID.randomUUID();

        List<String> permissions = rolePermissionService.getGrantedPermissions(tenantId, UserRole.OWNER);

        assertEquals(RolePermissionDefaults.allPermissionValues().size(), permissions.size());
        assertTrue(permissions.contains(Permission.SETTINGS_ROLES.getValue()));
    }

    @Test
    void getGrantedPermissions_artistReturnsGrantedOnly() {
        UUID tenantId = UUID.randomUUID();
        List<RolePermission> artistPermissions = List.of(
                RolePermission.builder()
                        .tenantId(tenantId)
                        .role(UserRole.ARTIST)
                        .permission(Permission.CALENDAR_VIEW_OWN.getValue())
                        .granted(true)
                        .build(),
                RolePermission.builder()
                        .tenantId(tenantId)
                        .role(UserRole.ARTIST)
                        .permission(Permission.REQUESTS_CREATE.getValue())
                        .granted(false)
                        .build()
        );

        when(rolePermissionRepository.findByTenantId(tenantId)).thenReturn(List.of(artistPermissions.getFirst()));
        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, UserRole.ARTIST)).thenReturn(artistPermissions);

        List<String> permissions = rolePermissionService.getGrantedPermissions(tenantId, UserRole.ARTIST);

        assertEquals(List.of(Permission.CALENDAR_VIEW_OWN.getValue()), permissions);
    }

    @Test
    void updateRolePermissions_replacesPermissionsForOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissions(List.of(
                Permission.CALENDAR_VIEW_OWN.getValue(),
                Permission.CLIENTS_VIEW_OWN.getValue()
        ));

        RolePermissionsDto updated = rolePermissionService.updateRolePermissions("artist", request);

        assertEquals("artist", updated.getRole());
        assertEquals(request.getPermissions(), updated.getPermissions());
        verify(rolePermissionRepository).deleteByTenantIdAndRole(tenantId, UserRole.ARTIST);
        verify(rolePermissionRepository, times(2)).save(any(RolePermission.class));
    }

    @Test
    void updateRolePermissions_rejectsNonOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateArtist(tenantId);

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissions(List.of(Permission.CALENDAR_VIEW_OWN.getValue()));

        assertThrows(AccessDeniedException.class,
                () -> rolePermissionService.updateRolePermissions("artist", request));
    }

    private void authenticateOwner(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateArtist(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.ARTIST)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
