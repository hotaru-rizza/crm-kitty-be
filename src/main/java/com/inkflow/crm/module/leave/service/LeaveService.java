package com.inkflow.crm.module.leave.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.security.SecurityUtils;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.enums.LeaveType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.leave.dto.CreateLeaveRequest;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import com.inkflow.crm.module.leave.dto.UpdateLeaveStatusRequest;
import com.inkflow.crm.module.leave.mapper.LeaveRequestMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final StaffRepository staffRepository;
    private final LeaveRequestMapper leaveMapper;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeavesByStaffId(UUID staffId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffId(tenantId, staffId);
        return leaveMapper.toDtoList(leaves);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeavesByStaffIdAndDateRange(UUID staffId, LocalDate startDate, LocalDate endDate) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffIdAndDateRange(tenantId, staffId, startDate, endDate);
        return leaveMapper.toDtoList(leaves);
    }

    @Transactional(readOnly = true)
    public LeaveRequestDto getLeaveById(UUID id) {
        LeaveRequest leave = findLeaveById(id);
        return leaveMapper.toDto(leave);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestDto> getAllLeaves(String status, String leaveType, LocalDate from, LocalDate to,
                                              UUID locationId, List<UUID> staffIds, int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);

        StringBuilder jpql = new StringBuilder("SELECT DISTINCT lr FROM LeaveRequest lr JOIN lr.staff s");
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(DISTINCT lr) FROM LeaveRequest lr JOIN lr.staff s");
        Map<String, Object> params = new HashMap<>();

        if (locationId != null) {
            jpql.append(" JOIN s.locations l");
            countJpql.append(" JOIN s.locations l");
        }

        String where = " WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL";
        jpql.append(where);
        countJpql.append(where);
        params.put("tenantId", tenantId);

        if (status != null && !status.isEmpty()) {
            LeaveStatus leaveStatus = LeaveStatus.valueOf(status.toUpperCase());
            jpql.append(" AND lr.status = :status");
            countJpql.append(" AND lr.status = :status");
            params.put("status", leaveStatus);
        }
        if (leaveType != null && !leaveType.isEmpty()) {
            LeaveType lt = LeaveType.valueOf(leaveType.toUpperCase());
            jpql.append(" AND lr.leaveType = :leaveType");
            countJpql.append(" AND lr.leaveType = :leaveType");
            params.put("leaveType", lt);
        }
        if (from != null && to != null) {
            jpql.append(" AND lr.endDate >= :from AND lr.startDate <= :to");
            countJpql.append(" AND lr.endDate >= :from AND lr.startDate <= :to");
            params.put("from", from);
            params.put("to", to);
        }
        if (locationId != null) {
            jpql.append(" AND l.id = :locationId");
            countJpql.append(" AND l.id = :locationId");
            params.put("locationId", locationId);
        }
        if (staffIds != null && !staffIds.isEmpty()) {
            jpql.append(" AND s.id IN :staffIds");
            countJpql.append(" AND s.id IN :staffIds");
            params.put("staffIds", staffIds);
        }

        jpql.append(" ORDER BY lr.createdAt DESC");

        TypedQuery<LeaveRequest> query = entityManager.createQuery(jpql.toString(), LeaveRequest.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        params.forEach((k, v) -> { query.setParameter(k, v); countQuery.setParameter(k, v); });

        long total = countQuery.getSingleResult();
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        Page<LeaveRequest> leavePage = new PageImpl<>(query.getResultList(), pageable, total);
        return leavePage.map(leaveMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long getPendingCount(UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        if (locationId != null) {
            return leaveRequestRepository.countPendingByLocation(tenantId, locationId);
        }
        return leaveRequestRepository.countPending(tenantId);
    }

    @Transactional
    public LeaveRequestDto createLeave(CreateLeaveRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        
        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getStaffId().toString()));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessRuleException("End date cannot be before start date");
        }

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlappingLeaves(
                tenantId, request.getStaffId(), request.getStartDate(), request.getEndDate());
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException("Leave request overlaps with existing approved leave");
        }

        LeaveType leaveType;
        try {
            leaveType = LeaveType.valueOf(request.getLeaveType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid leave type: " + request.getLeaveType());
        }

        boolean shouldAutoApprove = isAutoApproveEligible(currentUserId, staff);

        LeaveRequest leave = LeaveRequest.builder()
                .tenantId(tenantId)
                .staff(staff)
                .leaveType(leaveType)
                .status(shouldAutoApprove ? LeaveStatus.APPROVED : LeaveStatus.PENDING)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .notes(request.getNotes())
                .build();

        if (shouldAutoApprove) {
            Staff approver = staffRepository.findByAuthUserIdAndDeletedAtIsNull(currentUserId.toString())
                    .orElse(null);
            leave.setApprovedBy(approver);
            leave.setApprovedAt(Instant.now());
        }

        leave = leaveRequestRepository.save(leave);
        return leaveMapper.toDto(leave);
    }

    @Transactional
    public LeaveRequestDto updateLeaveStatus(UUID id, UpdateLeaveStatusRequest request) {
        LeaveRequest leave = findLeaveById(id);
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        LeaveStatus newStatus;
        try {
            newStatus = LeaveStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid status: " + request.getStatus());
        }

        if (newStatus == LeaveStatus.PENDING) {
            throw new BusinessRuleException("Cannot set status back to PENDING");
        }

        if (newStatus == LeaveStatus.APPROVED) {
            UUID tenantId = SecurityUtils.getCurrentTenantId();
            UUID leaveId = leave.getId();
            List<LeaveRequest> overlapping = leaveRequestRepository.findOverlappingLeaves(
                    tenantId, leave.getStaff().getId(), leave.getStartDate(), leave.getEndDate());
            overlapping = overlapping.stream()
                    .filter(l -> !l.getId().equals(leaveId))
                    .collect(Collectors.toList());
            if (!overlapping.isEmpty()) {
                throw new BusinessRuleException("Leave request overlaps with existing approved leave");
            }
        }

        leave.setStatus(newStatus);
        
        if (newStatus == LeaveStatus.APPROVED || newStatus == LeaveStatus.REJECTED) {
            Staff approver = staffRepository.findByAuthUserIdAndDeletedAtIsNull(currentUserId.toString())
                    .orElse(null);
            leave.setApprovedBy(approver);
            leave.setApprovedAt(Instant.now());
        }

        if (request.getNotes() != null) {
            leave.setNotes(request.getNotes());
        }

        leave = leaveRequestRepository.save(leave);
        return leaveMapper.toDto(leave);
    }

    @Transactional
    public LeaveRequestDto cancelLeave(UUID id) {
        LeaveRequest leave = findLeaveById(id);

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new BusinessRuleException("Cannot cancel a rejected leave request");
        }

        leave.setStatus(LeaveStatus.CANCELLED);
        leave = leaveRequestRepository.save(leave);
        return leaveMapper.toDto(leave);
    }

    @Transactional
    public void deleteLeave(UUID id) {
        LeaveRequest leave = findLeaveById(id);
        leave.softDelete();
        leaveRequestRepository.save(leave);
    }

    @Transactional(readOnly = true)
    public boolean isStaffOnLeave(UUID staffId, LocalDate date) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findActiveLeaveForDate(tenantId, staffId, date);
        return !activeLeaves.isEmpty();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getApprovedLeavesForDateRange(LocalDate from, LocalDate to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedInRange(tenantId, from, to);
        return leaveMapper.toDtoList(leaves);
    }

    private boolean isAutoApproveEligible(UUID currentUserId, Staff targetStaff) {
        Staff currentStaff = staffRepository.findByAuthUserIdAndDeletedAtIsNull(currentUserId.toString())
                .orElse(null);
        if (currentStaff == null) return false;

        if (currentStaff.getRole() == UserRole.OWNER) return true;

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        long activeStaffCount = staffRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
        if (activeStaffCount <= 1) return true;

        return false;
    }

    private LeaveRequest findLeaveById(UUID id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Leave request not found: " + id));
    }
}
