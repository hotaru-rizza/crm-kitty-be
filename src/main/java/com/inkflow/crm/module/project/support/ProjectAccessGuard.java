package com.inkflow.crm.module.project.support;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {

    private final RolePermissionService rolePermissionService;

    public void requireView(Project project) {
        if (!canView(project)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public void requireEdit(Project project) {
        if (!canMutate(project)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public void requireLeadReassignment(UUID newLeadArtistId) {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return;
        }
        if (hasProjectsViewAll()) {
            return;
        }
        throw AccessDeniedException.insufficientPermissions();
    }

    private boolean canView(Project project) {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return true;
        }
        if (hasProjectsViewAll()) {
            return true;
        }
        return hasProjectsViewOwn() && isLeadArtist(project);
    }

    private boolean canMutate(Project project) {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return true;
        }
        if (hasProjectsViewAll()) {
            return true;
        }
        return isLeadArtist(project);
    }

    private boolean isLeadArtist(Project project) {
        UUID leadArtistId = project.getArtist() != null ? project.getArtist().getId() : null;
        return leadArtistId != null && SecurityUtils.getCurrentUserId().equals(leadArtistId);
    }

    private boolean hasProjectsViewAll() {
        return hasPermission(Permission.PROJECTS_VIEW_ALL);
    }

    private boolean hasProjectsViewOwn() {
        return hasPermission(Permission.PROJECTS_VIEW_OWN);
    }

    private boolean hasPermission(Permission permission) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = SecurityUtils.getCurrentUserRole();
        return rolePermissionService.hasPermission(tenantId, role, permission.getValue());
    }
}
