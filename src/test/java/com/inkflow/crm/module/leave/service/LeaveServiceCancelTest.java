package com.inkflow.crm.module.leave.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import com.inkflow.crm.module.leave.mapper.LeaveRequestMapper;
import com.inkflow.crm.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceCancelTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private LeaveRequestMapper leaveMapper;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private LeaveService leaveService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cancelLeave_setsCancelledStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId);

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .status(LeaveStatus.PENDING)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .build();

        when(leaveRequestRepository.findById(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);
        when(leaveMapper.toDto(leave)).thenReturn(LeaveRequestDto.builder().id(leaveId).status("cancelled").build());

        LeaveRequestDto result = leaveService.cancelLeave(leaveId);

        assertEquals(LeaveStatus.CANCELLED, leave.getStatus());
        assertEquals("cancelled", result.getStatus());
        verify(leaveRequestRepository).save(leave);
    }

    @Test
    void cancelLeave_rejectsRejectedRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId);

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .status(LeaveStatus.REJECTED)
                .build();

        when(leaveRequestRepository.findById(leaveId)).thenReturn(Optional.of(leave));

        assertThrows(BusinessRuleException.class, () -> leaveService.cancelLeave(leaveId));
    }

    @Test
    void shouldCancelApprovedLeaveWhenStatusIsApproved() {
        UUID tenantId = UUID.randomUUID();
        UUID leaveId = UUID.randomUUID();
        authenticate(tenantId);

        LeaveRequest leave = LeaveRequest.builder()
                .id(leaveId)
                .tenantId(tenantId)
                .status(LeaveStatus.APPROVED)
                .staff(Staff.builder().id(UUID.randomUUID()).build())
                .build();

        when(leaveRequestRepository.findById(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(leave)).thenReturn(leave);
        when(leaveMapper.toDto(leave)).thenReturn(LeaveRequestDto.builder().id(leaveId).status("cancelled").build());

        LeaveRequestDto result = leaveService.cancelLeave(leaveId);

        assertEquals(LeaveStatus.CANCELLED, leave.getStatus());
        assertEquals("cancelled", result.getStatus());
    }

    private void authenticate(UUID tenantId) {
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
