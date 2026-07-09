package com.inkflow.crm.module.google.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.module.staff.service.StaffLookup;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarAccessGuardTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private GoogleCalendarAccessGuard accessGuard;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void requireManageAccess_allowsSelf() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).build();

        SecurityTestSupport.authenticate(staffId, tenantId, UserRole.ARTIST);
        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        Staff result = accessGuard.requireManageAccess(staffId);

        assertEquals(staff, result);
    }

    @Test
    void requireManageAccess_allowsOwnerForOtherStaff() {
        UUID ownerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).build();

        SecurityTestSupport.authenticate(ownerId, tenantId, UserRole.OWNER);
        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        Staff result = accessGuard.requireManageAccess(staffId);

        assertEquals(staff, result);
    }

    @Test
    void requireManageAccess_deniesOtherStaffWithoutPermission() {
        UUID currentUserId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).build();

        SecurityTestSupport.authenticate(currentUserId, tenantId, UserRole.ARTIST);
        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.SETTINGS_ACCESS.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.STAFF_EDIT.getValue()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireManageAccess(staffId));
    }
}
