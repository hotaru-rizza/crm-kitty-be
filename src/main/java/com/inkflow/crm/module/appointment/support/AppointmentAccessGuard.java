package com.inkflow.crm.module.appointment.support;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppointmentAccessGuard {

    private final RolePermissionService rolePermissionService;

    public void requireView(Appointment appointment) {
        if (!canView(appointment)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public void requireEdit(Appointment appointment) {
        if (!canMutate(appointment)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public void requireCancel(Appointment appointment) {
        if (!canMutate(appointment)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public void requireAssignableArtist(UUID requestedArtistId) {
        if (canAssignAnyArtist()) {
            return;
        }
        if (!SecurityUtils.getCurrentUserId().equals(requestedArtistId)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    private boolean canView(Appointment appointment) {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return true;
        }
        if (hasCalendarViewAll()) {
            return true;
        }
        return hasCalendarViewOwn() && isAssignedArtist(appointment);
    }

    private boolean canMutate(Appointment appointment) {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return true;
        }
        if (hasCalendarViewAll()) {
            return true;
        }
        return isAssignedArtist(appointment);
    }

    private boolean canAssignAnyArtist() {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return true;
        }
        return hasCalendarViewAll();
    }

    private boolean isAssignedArtist(Appointment appointment) {
        return appointment.getArtist() != null
                && SecurityUtils.getCurrentUserId().equals(appointment.getArtist().getId());
    }

    private boolean hasCalendarViewAll() {
        return hasPermission(Permission.CALENDAR_VIEW_ALL);
    }

    private boolean hasCalendarViewOwn() {
        return hasPermission(Permission.CALENDAR_VIEW_OWN);
    }

    private boolean hasPermission(Permission permission) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = SecurityUtils.getCurrentUserRole();
        return rolePermissionService.hasPermission(tenantId, role, permission.getValue());
    }
}
