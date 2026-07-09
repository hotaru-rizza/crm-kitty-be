package com.inkflow.crm.module.project.support;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private ProjectAccessGuard accessGuard;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID artistAId = UUID.randomUUID();
    private final UUID artistBId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void requireView_allowsOwnerForAnyProject() {
        Project project = projectFor(artistBId);
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.OWNER);

        assertDoesNotThrow(() -> accessGuard.requireView(project));
    }

    @Test
    void requireView_allowsAdminWithViewAll() {
        Project project = projectFor(artistBId);
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.ADMIN);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ADMIN, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(true);

        assertDoesNotThrow(() -> accessGuard.requireView(project));
    }

    @Test
    void requireView_allowsArtistForOwnLeadProject() {
        Project project = projectFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_OWN.getValue()))
                .thenReturn(true);

        assertDoesNotThrow(() -> accessGuard.requireView(project));
    }

    @Test
    void requireView_deniesArtistForAnotherLeadsProject() {
        Project project = projectFor(artistBId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_OWN.getValue()))
                .thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireView(project));
    }

    @Test
    void requireEdit_allowsArtistForOwnLeadProject() {
        Project project = projectFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertDoesNotThrow(() -> accessGuard.requireEdit(project));
    }

    @Test
    void requireEdit_deniesArtistForAnotherLeadsProject() {
        Project project = projectFor(artistBId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireEdit(project));
    }

    @Test
    void requireLeadReassignment_deniesArtistWithoutViewAll() {
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireLeadReassignment(artistBId));
    }

    @Test
    void requireView_deniesWhenNoProjectPermissions() {
        Project project = projectFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), eq(Permission.PROJECTS_VIEW_ALL.getValue())))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), eq(Permission.PROJECTS_VIEW_OWN.getValue())))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireView(project));
    }

    private static Project projectFor(UUID artistId) {
        Staff artist = Staff.builder().id(artistId).build();
        return Project.builder().artist(artist).build();
    }
}
