package com.inkflow.crm.module.leave.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.leave.dto.CreateLeaveRequest;
import com.inkflow.crm.support.AuditMocks;
import org.junit.jupiter.api.BeforeEach;
import com.inkflow.crm.module.leave.mapper.LeaveRequestMapper;
import com.inkflow.crm.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private LeaveRequestMapper leaveMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private LeaveService leaveService;

    @BeforeEach
    void stubAudit() {
        AuditMocks.stubLabelFormatter(auditLabelFormatter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createLeave_rejectsEndDateBeforeStartDate() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, staffId);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 5))
                .build();

        assertThrows(BusinessRuleException.class, () -> leaveService.createLeave(request));
    }

    @Test
    void createLeave_rejectsInvalidLeaveType() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, staffId);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("unknown_type")
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 12))
                .build();

        assertThrows(BusinessRuleException.class, () -> leaveService.createLeave(request));
    }

    @Test
    void createLeave_rejectsOverlappingApprovedLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, staffId);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        LeaveRequest existing = LeaveRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .staff(staff)
                .status(LeaveStatus.APPROVED)
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 12))
                .build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(leaveRequestRepository.findOverlappingLeaves(staffId,
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 13))).thenReturn(List.of(existing));

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 6, 11))
                .endDate(LocalDate.of(2026, 6, 13))
                .build();

        assertThrows(BusinessRuleException.class, () -> leaveService.createLeave(request));
    }

    @Test
    void createLeave_rejectsOverlappingPendingLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, staffId);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        LeaveRequest existing = LeaveRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .staff(staff)
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 16))
                .endDate(LocalDate.of(2026, 7, 16))
                .build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(leaveRequestRepository.findOverlappingLeaves(staffId,
                LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 16))).thenReturn(List.of(existing));

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 7, 16))
                .endDate(LocalDate.of(2026, 7, 16))
                .build();

        assertThrows(BusinessRuleException.class, () -> leaveService.createLeave(request));
    }

    @Test
    void isStaffOnLeave_returnsTrueWhenActiveLeaveExists() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, staffId);

        when(leaveRequestRepository.findActiveLeaveForDate(staffId, LocalDate.of(2026, 6, 15)))
                .thenReturn(List.of(LeaveRequest.builder().build()));

        assertTrue(leaveService.isStaffOnLeave(staffId, LocalDate.of(2026, 6, 15)));
    }

    @Test
    void isStaffOnLeave_returnsFalseWhenNoActiveLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, staffId);

        when(leaveRequestRepository.findActiveLeaveForDate(staffId, LocalDate.of(2026, 6, 15)))
                .thenReturn(List.of());

        assertFalse(leaveService.isStaffOnLeave(staffId, LocalDate.of(2026, 6, 15)));
    }

    @Test
    void getPendingCount_returnsTenantWideCount() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(leaveRequestRepository.countPending()).thenReturn(3L);

        assertEquals(3L, leaveService.getPendingCount(null));
    }

    @Test
    void shouldReturnLocationPendingCountWhenLocationIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(leaveRequestRepository.countPendingByLocation(locationId)).thenReturn(2L);

        assertEquals(2L, leaveService.getPendingCount(locationId));
        verify(leaveRequestRepository).countPendingByLocation(locationId);
    }

    @Test
    void shouldThrowNotFoundWhenStaffDoesNotExistOnCreate() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.empty());

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 12))
                .build();

        assertThrows(ResourceNotFoundException.class, () -> leaveService.createLeave(request));
    }

    @Test
    void shouldLeavePendingWhenCreatorIsNotOwnerAndTenantHasMultipleStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID artistUserId = UUID.randomUUID();
        authenticate(tenantId, artistUserId, UserRole.ARTIST);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        Staff artist = Staff.builder().id(artistUserId).role(UserRole.ARTIST).build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(leaveRequestRepository.findOverlappingLeaves(any(), any(), any())).thenReturn(List.of());
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(artistUserId.toString())).thenReturn(Optional.of(artist));
        when(staffRepository.countByDeletedAtIsNull()).thenReturn(2L);
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(leaveMapper.toDto(any())).thenReturn(com.inkflow.crm.module.leave.dto.LeaveRequestDto.builder().status("PENDING").build());

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 12))
                .build();

        leaveService.createLeave(request);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertEquals(LeaveStatus.PENDING, captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getApprovedBy());
    }

    @Test
    void shouldAutoApproveWhenSoloTenantEvenForNonOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID artistUserId = UUID.randomUUID();
        authenticate(tenantId, artistUserId, UserRole.ARTIST);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        Staff artist = Staff.builder().id(artistUserId).role(UserRole.ARTIST).build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(leaveRequestRepository.findOverlappingLeaves(any(), any(), any())).thenReturn(List.of());
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(artistUserId.toString())).thenReturn(Optional.of(artist));
        when(staffRepository.countByDeletedAtIsNull()).thenReturn(1L);
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(leaveMapper.toDto(any())).thenReturn(com.inkflow.crm.module.leave.dto.LeaveRequestDto.builder().status("APPROVED").build());

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 6, 10))
                .endDate(LocalDate.of(2026, 6, 12))
                .build();

        leaveService.createLeave(request);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertEquals(LeaveStatus.APPROVED, captor.getValue().getStatus());
        assertEquals(artist, captor.getValue().getApprovedBy());
        assertNotNull(captor.getValue().getApprovedAt());
    }

    @Test
    void shouldThrowNotFoundWhenLeaveBelongsToOtherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(otherTenantId)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveService.getLeaveById(leaveId));
    }

    @Test
    void shouldThrowNotFoundWhenLeaveIsSoftDeleted() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveService.getLeaveById(leaveId));
    }

    @Test
    void deleteLeave_softDeletesRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .status(LeaveStatus.APPROVED)
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);

        leaveService.deleteLeave(leaveId);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    private void authenticate(UUID tenantId, UUID userId) {
        authenticate(tenantId, userId, UserRole.OWNER);
    }

    private void authenticate(UUID tenantId, UUID userId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
