package com.inkflow.crm.module.google.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.module.staff.service.StaffLookup;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoogleCalendarAccessGuard {

    private final StaffLookup staffLookup;
    private final RolePermissionService rolePermissionService;

    public Staff requireManageAccess(UUID staffId) {
        Staff staff = staffLookup.requireStaff(staffId);
        assertCanManage(staffId);
        return staff;
    }

    public Staff requireViewAccess(UUID staffId) {
        Staff staff = staffLookup.requireStaff(staffId);
        assertCanView(staffId);
        return staff;
    }

    private void assertCanManage(UUID staffId) {
        if (canManage(staffId)) {
            return;
        }
        throw AccessDeniedException.insufficientPermissions();
    }

    private void assertCanView(UUID staffId) {
        if (canView(staffId)) {
            return;
        }
        throw AccessDeniedException.insufficientPermissions();
    }

    private boolean canManage(UUID staffId) {
        if (staffId.equals(SecurityUtils.getCurrentUserId())) {
            return true;
        }
        return hasStaffPermission(Permission.SETTINGS_ACCESS, Permission.STAFF_EDIT);
    }

    private boolean canView(UUID staffId) {
        if (staffId.equals(SecurityUtils.getCurrentUserId())) {
            return true;
        }
        return hasStaffPermission(Permission.SETTINGS_ACCESS, Permission.STAFF_EDIT, Permission.STAFF_VIEW);
    }

    private boolean hasStaffPermission(Permission... permissions) {
        UserRole role = SecurityUtils.getCurrentUserRole();
        if (role == UserRole.OWNER) {
            return true;
        }

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        for (Permission permission : permissions) {
            if (rolePermissionService.hasPermission(tenantId, role, permission.getValue())) {
                return true;
            }
        }
        return false;
    }
}
