package com.inkflow.crm.module.leave.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.enums.LeaveType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import com.inkflow.crm.support.AuditMocks;
import org.junit.jupiter.api.BeforeEach;
import com.inkflow.crm.module.leave.dto.UpdateLeaveStatusRequest;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceStatusTest {

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
    void updateLeaveStatus_approvesPendingLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        authenticate(tenantId, managerId);

        Staff staff = Staff.builder().id(staffId).firstName("Alex").lastName("Ink").build();
        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(staff)
                .leaveType(LeaveType.VACATION)
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.findOverlappingLeaves(staffId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3))).thenReturn(List.of());
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(managerId.toString()))
                .thenReturn(Optional.of(Staff.builder().id(managerId).role(UserRole.OWNER).build()));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);
        when(leaveMapper.toDto(leave)).thenReturn(LeaveRequestDto.builder().id(leaveId).status("APPROVED").build());

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("approved");

        LeaveRequestDto result = leaveService.updateLeaveStatus(leaveId, request);

        assertEquals(LeaveStatus.APPROVED, leave.getStatus());
        assertEquals("APPROVED", result.getStatus());
        verify(leaveRequestRepository).save(leave);
    }

    @Test
    void updateLeaveStatus_rejectsPendingStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .status(LeaveStatus.APPROVED)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("pending");

        assertThrows(BusinessRuleException.class, () -> leaveService.updateLeaveStatus(leaveId, request));
    }

    @Test
    void createLeave_autoApprovesForOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        authenticate(tenantId, ownerId);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        Staff owner = Staff.builder().id(ownerId).role(UserRole.OWNER).build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(leaveRequestRepository.findOverlappingLeaves(any(), any(), any())).thenReturn(List.of());
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(ownerId.toString())).thenReturn(Optional.of(owner));
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(leaveMapper.toDto(any())).thenReturn(LeaveRequestDto.builder().status("APPROVED").build());

        var request = com.inkflow.crm.module.leave.dto.CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 2))
                .build();

        LeaveRequestDto result = leaveService.createLeave(request);

        assertEquals("APPROVED", result.getStatus());
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
    }

    @Test
    void shouldRejectInvalidStatusWhenUpdatingLeaveStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("not_a_status");

        assertThrows(BusinessRuleException.class, () -> leaveService.updateLeaveStatus(leaveId, request));
    }

    @Test
    void shouldRejectOverlappingLeaveWhenApproving() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Staff staff = Staff.builder().id(staffId).build();
        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(staff)
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .build();
        LeaveRequest conflicting = LeaveRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .staff(staff)
                .status(LeaveStatus.APPROVED)
                .startDate(LocalDate.of(2026, 7, 2))
                .endDate(LocalDate.of(2026, 7, 4))
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.findOverlappingLeaves(staffId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3))).thenReturn(List.of(conflicting));

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("APPROVED");

        assertThrows(BusinessRuleException.class, () -> leaveService.updateLeaveStatus(leaveId, request));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void shouldAllowSelfOverlapWhenApprovingSameLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        authenticate(tenantId, managerId);

        Staff staff = Staff.builder().id(staffId).build();
        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(staff)
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.findOverlappingLeaves(staffId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3))).thenReturn(List.of(leave));
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(managerId.toString()))
                .thenReturn(Optional.of(Staff.builder().id(managerId).role(UserRole.OWNER).build()));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);
        when(leaveMapper.toDto(leave)).thenReturn(LeaveRequestDto.builder().id(leaveId).status("APPROVED").build());

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("approved");

        LeaveRequestDto result = leaveService.updateLeaveStatus(leaveId, request);

        assertEquals(LeaveStatus.APPROVED, leave.getStatus());
        assertEquals("APPROVED", result.getStatus());
    }

    @Test
    void shouldSetRejectedStatusAndApproverWhenRejectingLeave() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        authenticate(tenantId, managerId);

        Staff manager = Staff.builder().id(managerId).role(UserRole.OWNER).build();
        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(managerId.toString())).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);
        when(leaveMapper.toDto(leave)).thenReturn(LeaveRequestDto.builder().id(leaveId).status("REJECTED").build());

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("rejected");

        leaveService.updateLeaveStatus(leaveId, request);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertEquals(LeaveStatus.REJECTED, captor.getValue().getStatus());
        assertEquals(manager, captor.getValue().getApprovedBy());
        assertNotNull(captor.getValue().getApprovedAt());
    }

    @Test
    void shouldUpdateNotesWhenProvidedOnStatusUpdate() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 3))
                .notes("original")
                .build();

        when(leaveRequestRepository.findByIdAndDeletedAtIsNull(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);
        when(leaveMapper.toDto(leave)).thenReturn(LeaveRequestDto.builder().id(leaveId).status("CANCELLED").build());

        UpdateLeaveStatusRequest request = new UpdateLeaveStatusRequest();
        request.setStatus("cancelled");
        request.setNotes("manager note");

        leaveService.updateLeaveStatus(leaveId, request);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertEquals("manager note", captor.getValue().getNotes());
        assertEquals(LeaveStatus.CANCELLED, captor.getValue().getStatus());
    }

    @Test
    void shouldCreatePendingLeaveWhenCurrentStaffRecordMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(tenantId, userId);

        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(leaveRequestRepository.findOverlappingLeaves(any(), any(), any())).thenReturn(List.of());
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(userId.toString())).thenReturn(Optional.empty());
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(leaveMapper.toDto(any())).thenReturn(LeaveRequestDto.builder().status("PENDING").build());

        var request = com.inkflow.crm.module.leave.dto.CreateLeaveRequest.builder()
                .staffId(staffId)
                .leaveType("vacation")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 2))
                .build();

        leaveService.createLeave(request);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertEquals(LeaveStatus.PENDING, captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getApprovedBy());
    }

    private void authenticate(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
