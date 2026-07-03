package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.infrastructure.supabase.SupabaseAdminService;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.staff.dto.DeactivateStaffRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffLifecycleServiceTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private SupabaseAdminService supabaseAdminService;

    @Mock
    private AuditRecorder auditRecorder;

    @InjectMocks
    private StaffLifecycleService staffLifecycleService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getFutureAppointmentsCount_returnsUpcomingCount() {
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(staffId),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new com.inkflow.crm.domain.entity.Appointment(), new com.inkflow.crm.domain.entity.Appointment()));

        assertEquals(2, staffLifecycleService.getFutureAppointmentsCount(staffId));
    }

    @Test
    void reactivateStaff_setsActiveStatus() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .accountStatus(AccountStatus.DEACTIVATED)
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        staffLifecycleService.reactivateStaff(staffId);

        assertEquals(AccountStatus.ACTIVE, staff.getAccountStatus());
        verify(staffRepository).save(staff);
    }

    @Test
    void deactivateStaff_rejectsAlreadyDeactivated() {
        UUID staffId = UUID.randomUUID();
        authenticateOwner(UUID.randomUUID());

        Staff staff = Staff.builder()
                .id(staffId)
                .accountStatus(AccountStatus.DEACTIVATED)
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        assertThrows(BusinessRuleException.class,
                () -> staffLifecycleService.deactivateStaff(staffId, new DeactivateStaffRequest(false)));
    }

    @Test
    void reactivateStaff_rejectsWhenNotDeactivated() {
        UUID staffId = UUID.randomUUID();
        authenticateOwner(UUID.randomUUID());

        Staff staff = Staff.builder()
                .id(staffId)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffLifecycleService.reactivateStaff(staffId));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getErrorCode());
    }

    @Test
    void deactivateStaff_setsDeactivatedAndDisablesOnlineBooking() {
        UUID staffId = UUID.randomUUID();
        authenticateOwner(UUID.randomUUID());

        Staff staff = Staff.builder()
                .id(staffId)
                .accountStatus(AccountStatus.ACTIVE)
                .availableForOnlineBooking(true)
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        staffLifecycleService.deactivateStaff(staffId, new DeactivateStaffRequest(false));

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(captor.capture());
        Staff saved = captor.getValue();
        assertEquals(AccountStatus.DEACTIVATED, saved.getAccountStatus());
        assertFalse(saved.getAvailableForOnlineBooking());
        verify(supabaseAdminService, never()).revokeAllSessions(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivateStaff_cancelsFutureAppointmentsWhenRequested() {
        UUID staffId = UUID.randomUUID();
        authenticateOwner(UUID.randomUUID());

        Staff staff = Staff.builder()
                .id(staffId)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        Appointment upcoming = Appointment.builder()
                .status(AppointmentStatus.SCHEDULED)
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(staffId),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(upcoming));

        staffLifecycleService.deactivateStaff(staffId, new DeactivateStaffRequest(true));

        ArgumentCaptor<List<Appointment>> captor = ArgumentCaptor.forClass(List.class);
        verify(appointmentRepository).saveAll(captor.capture());
        assertEquals(AppointmentStatus.CANCELLED, captor.getValue().get(0).getStatus());
    }

    @Test
    void deactivateStaff_revokesSessionsWhenAuthUserPresent() {
        UUID staffId = UUID.randomUUID();
        authenticateOwner(UUID.randomUUID());

        Staff staff = Staff.builder()
                .id(staffId)
                .accountStatus(AccountStatus.ACTIVE)
                .authUserId("auth-user-1")
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        staffLifecycleService.deactivateStaff(staffId, new DeactivateStaffRequest(false));

        verify(supabaseAdminService).revokeAllSessions("auth-user-1");
    }

    private void authenticateOwner(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
