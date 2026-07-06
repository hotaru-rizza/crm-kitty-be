package com.inkflow.crm.module.appointment.support;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Appointment;
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
class AppointmentAccessGuardTest {

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private AppointmentAccessGuard accessGuard;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID artistAId = UUID.randomUUID();
    private final UUID artistBId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void requireView_allowsOwnerForAnyAppointment() {
        Appointment appointment = appointmentFor(artistBId);
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.OWNER);

        assertDoesNotThrow(() -> accessGuard.requireView(appointment));
    }

    @Test
    void requireView_allowsAdminWithViewAll() {
        Appointment appointment = appointmentFor(artistBId);
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.ADMIN);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ADMIN, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(true);

        assertDoesNotThrow(() -> accessGuard.requireView(appointment));
    }

    @Test
    void requireView_allowsArtistForOwnAppointment() {
        Appointment appointment = appointmentFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_OWN.getValue()))
                .thenReturn(true);

        assertDoesNotThrow(() -> accessGuard.requireView(appointment));
    }

    @Test
    void requireView_deniesArtistForAnotherArtistsAppointment() {
        Appointment appointment = appointmentFor(artistBId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_OWN.getValue()))
                .thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireView(appointment));
    }

    @Test
    void requireEdit_allowsArtistForOwnAppointment() {
        Appointment appointment = appointmentFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertDoesNotThrow(() -> accessGuard.requireEdit(appointment));
    }

    @Test
    void requireEdit_deniesArtistForAnotherArtistsAppointment() {
        Appointment appointment = appointmentFor(artistBId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireEdit(appointment));
    }

    @Test
    void requireCancel_allowsArtistForOwnAppointment() {
        Appointment appointment = appointmentFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertDoesNotThrow(() -> accessGuard.requireCancel(appointment));
    }

    @Test
    void requireCancel_deniesArtistForAnotherArtistsAppointment() {
        Appointment appointment = appointmentFor(artistBId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireCancel(appointment));
    }

    @Test
    void requireView_deniesWhenNoCalendarPermissions() {
        Appointment appointment = appointmentFor(artistAId);
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), eq(Permission.CALENDAR_VIEW_ALL.getValue())))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), eq(Permission.CALENDAR_VIEW_OWN.getValue())))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireView(appointment));
    }

    @Test
    void requireAssignableArtist_deniesArtistAssigningOtherArtist() {
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireAssignableArtist(artistBId));
    }

    @Test
    void requireAssignableArtist_allowsArtistAssigningSelf() {
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CALENDAR_VIEW_ALL.getValue()))
                .thenReturn(false);

        assertDoesNotThrow(() -> accessGuard.requireAssignableArtist(artistAId));
    }

    private static Appointment appointmentFor(UUID artistId) {
        Staff artist = Staff.builder().id(artistId).build();
        return Appointment.builder().artist(artist).build();
    }
}
