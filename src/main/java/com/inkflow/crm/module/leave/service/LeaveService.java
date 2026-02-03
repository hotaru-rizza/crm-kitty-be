package com.inkflow.crm.module.leave.service;

import com.inkflow.crm.common.exception.BadRequestException;
import com.inkflow.crm.common.exception.NotFoundException;
import com.inkflow.crm.common.util.SecurityUtils;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.enums.LeaveType;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.leave.dto.CreateLeaveRequest;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import com.inkflow.crm.module.leave.dto.UpdateLeaveStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final StaffRepository staffRepository;

    public List<LeaveRequestDto> getLeavesByStaffId(UUID staffId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffId(tenantId, staffId);
        return leaves.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<LeaveRequestDto> getLeavesByStaffIdAndDateRange(UUID staffId, LocalDate startDate, LocalDate endDate) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffIdAndDateRange(tenantId, staffId, startDate, endDate);
        return leaves.stream().map(this::toDto).collect(Collectors.toList());
    }

    public LeaveRequestDto getLeaveById(UUID id) {
        LeaveRequest leave = findLeaveById(id);
        return toDto(leave);
    }

    @Transactional
    public LeaveRequestDto createLeave(CreateLeaveRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        
        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new NotFoundException("Staff not found"));

        // Validate dates
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        // Check for overlapping leaves
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlappingLeaves(
                tenantId, request.getStaffId(), request.getStartDate(), request.getEndDate());
        if (!overlapping.isEmpty()) {
            throw new BadRequestException("Leave request overlaps with existing approved leave");
        }

        LeaveType leaveType;
        try {
            leaveType = LeaveType.valueOf(request.getLeaveType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid leave type: " + request.getLeaveType());
        }

        LeaveRequest leave = LeaveRequest.builder()
                .tenantId(tenantId)
                .staff(staff)
                .leaveType(leaveType)
                .status(LeaveStatus.PENDING)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .notes(request.getNotes())
                .build();

        leave = leaveRequestRepository.save(leave);
        return toDto(leave);
    }

    @Transactional
    public LeaveRequestDto updateLeaveStatus(UUID id, UpdateLeaveStatusRequest request) {
        LeaveRequest leave = findLeaveById(id);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        LeaveStatus newStatus;
        try {
            newStatus = LeaveStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + request.getStatus());
        }

        if (newStatus == LeaveStatus.PENDING) {
            throw new BadRequestException("Cannot set status back to PENDING");
        }

        // Check for overlapping leaves when approving
        if (newStatus == LeaveStatus.APPROVED) {
            UUID tenantId = SecurityUtils.getCurrentTenantId();
            List<LeaveRequest> overlapping = leaveRequestRepository.findOverlappingLeaves(
                    tenantId, leave.getStaff().getId(), leave.getStartDate(), leave.getEndDate());
            overlapping = overlapping.stream()
                    .filter(l -> !l.getId().equals(leave.getId()))
                    .collect(Collectors.toList());
            if (!overlapping.isEmpty()) {
                throw new BadRequestException("Leave request overlaps with existing approved leave");
            }
        }

        leave.setStatus(newStatus);
        
        if (newStatus == LeaveStatus.APPROVED || newStatus == LeaveStatus.REJECTED) {
            Staff approver = staffRepository.findByAuthUserId(currentUserId.toString())
                    .orElse(null);
            leave.setApprovedBy(approver);
            leave.setApprovedAt(Instant.now());
        }

        if (request.getNotes() != null) {
            leave.setNotes(request.getNotes());
        }

        leave = leaveRequestRepository.save(leave);
        return toDto(leave);
    }

    @Transactional
    public void deleteLeave(UUID id) {
        LeaveRequest leave = findLeaveById(id);
        leave.softDelete();
        leaveRequestRepository.save(leave);
    }

    public boolean isStaffOnLeave(UUID staffId, LocalDate date) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findActiveLeaveForDate(tenantId, staffId, date);
        return !activeLeaves.isEmpty();
    }

    private LeaveRequest findLeaveById(UUID id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Leave request not found"));
    }

    private LeaveRequestDto toDto(LeaveRequest leave) {
        return LeaveRequestDto.builder()
                .id(leave.getId())
                .staffId(leave.getStaff().getId())
                .staffName(leave.getStaff().getFullName())
                .staffAvatar(leave.getStaff().getAvatar())
                .leaveType(leave.getLeaveType().name())
                .status(leave.getStatus().name())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .daysCount(leave.getDaysCount())
                .reason(leave.getReason())
                .notes(leave.getNotes())
                .approvedById(leave.getApprovedBy() != null ? leave.getApprovedBy().getId() : null)
                .approvedByName(leave.getApprovedBy() != null ? leave.getApprovedBy().getFullName() : null)
                .approvedAt(leave.getApprovedAt())
                .createdAt(leave.getCreatedAt())
                .build();
    }
}
