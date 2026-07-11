package com.inkflow.crm.module.leave.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.dto.AuditContext;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
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
import com.inkflow.crm.module.leave.dto.LeaveQueryParts;
import com.inkflow.crm.module.leave.mapper.LeaveRequestMapper;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final StaffRepository staffRepository;
    private final LeaveRequestMapper leaveMapper;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeavesByStaffId(UUID staffId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return leaveMapper.toDtoList(leaveRequestRepository.findByStaffId( staffId));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeavesByStaffIdAndDateRange(UUID staffId, LocalDate startDate, LocalDate endDate) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return leaveMapper.toDtoList(leaveRequestRepository.findByStaffIdAndDateRange( staffId, startDate, endDate));
    }

    @Transactional(readOnly = true)
    public LeaveRequestDto getLeaveById(UUID id) {
        return leaveMapper.toDto(requireLeave(id));
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestDto> getAllLeaves(
            String status,
            String leaveType,
            LocalDate from,
            LocalDate to,
            UUID locationId,
            List<UUID> staffIds,
            int page,
            int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);
        UUID effectiveLocationId = LocationScope.resolveFilter(locationId).orElse(null);

        LeaveQueryParts query = buildFilterQuery(tenantId, status, leaveType, from, to, effectiveLocationId, staffIds);
        TypedQuery<LeaveRequest> dataQuery = entityManager.createQuery(query.dataJpql(), LeaveRequest.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(query.countJpql(), Long.class);
        query.params().forEach((key, value) -> {
            dataQuery.setParameter(key, value);
            countQuery.setParameter(key, value);
        });

        long total = countQuery.getSingleResult();
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        Page<LeaveRequest> leavePage = new PageImpl<>(dataQuery.getResultList(), pageable, total);
        return leavePage.map(leaveMapper::toDto);
    }

    @Transactional(readOnly = true)
    public long getPendingCount(UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID effectiveLocationId = LocationScope.resolveFilter(locationId).orElse(null);
        if (effectiveLocationId != null) {
            return leaveRequestRepository.countPendingByLocation(effectiveLocationId);
        }
        return leaveRequestRepository.countPending();
    }

    @Transactional
    public LeaveRequestDto createLeave(CreateLeaveRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Staff staff = staffRepository.findByIdAndDeletedAtIsNull(request.getStaffId())
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getStaffId().toString()));

        validateDateRange(request.getStartDate(), request.getEndDate());
        LeaveType leaveType = parseLeaveType(request.getLeaveType());
        String leaveLabel = auditLabelFormatter.leave(staff, leaveType, request.getStartDate(), request.getEndDate());
        validateNoOverlap(tenantId, request.getStaffId(), request.getStartDate(), request.getEndDate(), null, leaveLabel);

        boolean autoApprove = isAutoApproveEligible(currentUserId, staff);

        LeaveRequest leave = LeaveRequest.builder()
                .tenantId(tenantId)
                .staff(staff)
                .leaveType(leaveType)
                .status(autoApprove ? LeaveStatus.APPROVED : LeaveStatus.PENDING)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .notes(request.getNotes())
                .build();

        if (autoApprove) {
            applyApproval(leave, currentUserId);
        }

        leave = leaveRequestRepository.save(leave);
        log.info("Leave created: tenantId={} leaveId={} staffId={}", tenantId, leave.getId(), staff.getId());
        if (autoApprove) {
            auditRecorder.record(
                    AuditAction.CREATE,
                    AuditEntityType.LEAVE,
                    leave.getId().toString(),
                    leaveLabel,
                    null,
                    "Автоматично схвалено"
            );
        } else {
            auditRecorder.record(
                    AuditAction.CREATE,
                    AuditEntityType.LEAVE,
                    leave.getId().toString(),
                    leaveLabel
            );
        }
        return leaveMapper.toDto(leave);
    }

    @Transactional
    public LeaveRequestDto updateLeaveStatus(UUID id, UpdateLeaveStatusRequest request) {
        LeaveRequest leave = requireLeave(id);
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        LeaveStatus previousStatus = leave.getStatus();
        LeaveStatus newStatus = parseLeaveStatus(request.getStatus());
        String leaveLabel = auditLabelFormatter.leave(
                leave.getStaff(), leave.getLeaveType(), leave.getStartDate(), leave.getEndDate());

        if (newStatus == LeaveStatus.PENDING) {
            throw new BusinessRuleException("Cannot set status back to PENDING");
        }

        if (newStatus == LeaveStatus.APPROVED) {
            validateNoOverlap(tenantId, leave.getStaff().getId(), leave.getStartDate(), leave.getEndDate(), leave.getId(), leaveLabel);
        }

        leave.setStatus(newStatus);

        if (newStatus == LeaveStatus.APPROVED || newStatus == LeaveStatus.REJECTED) {
            applyApproval(leave, currentUserId);
        }
        if (request.getNotes() != null) {
            leave.setNotes(request.getNotes());
        }

        leave = leaveRequestRepository.save(leave);
        log.info("Leave status updated: tenantId={} leaveId={} status={}", tenantId, id, newStatus);
        auditRecorder.record(
                AuditAction.STATUS_CHANGE,
                AuditEntityType.LEAVE,
                leave.getId().toString(),
                leaveLabel,
                null,
                previousStatus.getDisplayName() + " → " + newStatus.getDisplayName()
        );
        return leaveMapper.toDto(leave);
    }

    @Transactional
    public LeaveRequestDto cancelLeave(UUID id) {
        LeaveRequest leave = requireLeave(id);

        if (leave.getStatus() == LeaveStatus.REJECTED) {
            throw new BusinessRuleException("Cannot cancel a rejected leave request");
        }

        leave.setStatus(LeaveStatus.CANCELLED);
        leave = leaveRequestRepository.save(leave);

        log.info("Leave cancelled: tenantId={} leaveId={}", leave.getTenantId(), id);
        auditRecorder.record(
                AuditAction.CANCEL,
                AuditEntityType.LEAVE,
                leave.getId().toString(),
                auditLabelFormatter.leave(
                        leave.getStaff(), leave.getLeaveType(), leave.getStartDate(), leave.getEndDate())
        );
        return leaveMapper.toDto(leave);
    }

    @Transactional
    public void deleteLeave(UUID id) {
        LeaveRequest leave = requireLeave(id);
        String leaveLabel = auditLabelFormatter.leave(
                leave.getStaff(), leave.getLeaveType(), leave.getStartDate(), leave.getEndDate());
        leave.softDelete();
        leaveRequestRepository.save(leave);
        log.info("Leave deleted: tenantId={} leaveId={}", leave.getTenantId(), id);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.LEAVE,
                leave.getId().toString(),
                leaveLabel
        );
    }

    @Transactional(readOnly = true)
    public boolean isStaffOnLeave(UUID staffId, LocalDate date) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return !leaveRequestRepository.findActiveLeaveForDate( staffId, date).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getApprovedLeavesForDateRange(LocalDate from, LocalDate to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return leaveMapper.toDtoList(leaveRequestRepository.findApprovedInRange( from, to));
    }

    private LeaveQueryParts buildFilterQuery(
            UUID tenantId,
            String status,
            String leaveType,
            LocalDate from,
            LocalDate to,
            UUID locationId,
            List<UUID> staffIds) {
        StringBuilder jpql = new StringBuilder("SELECT DISTINCT lr FROM LeaveRequest lr JOIN lr.staff s");
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(DISTINCT lr) FROM LeaveRequest lr JOIN lr.staff s");
        Map<String, Object> params = new HashMap<>();

        if (locationId != null) {
            jpql.append(" JOIN s.locations l");
            countJpql.append(" JOIN s.locations l");
        }

        jpql.append(" WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL");
        countJpql.append(" WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL");
        params.put("tenantId", tenantId);

        if (status != null && !status.isEmpty()) {
            jpql.append(" AND lr.status = :status");
            countJpql.append(" AND lr.status = :status");
            params.put("status", LeaveStatus.valueOf(status.toUpperCase()));
        }
        if (leaveType != null && !leaveType.isEmpty()) {
            jpql.append(" AND lr.leaveType = :leaveType");
            countJpql.append(" AND lr.leaveType = :leaveType");
            params.put("leaveType", LeaveType.valueOf(leaveType.toUpperCase()));
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
        return new LeaveQueryParts(jpql.toString(), countJpql.toString(), params);
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessRuleException("End date cannot be before start date");
        }
    }

    private void validateNoOverlap(
            UUID tenantId,
            UUID staffId,
            LocalDate start,
            LocalDate end,
            UUID excludeId,
            String leaveLabel) {
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlappingLeaves( staffId, start, end);
        boolean hasConflict = overlapping.stream()
                .anyMatch(leave -> excludeId == null || !leave.getId().equals(excludeId));

        if (hasConflict) {
            String entityId = excludeId != null ? excludeId.toString() : staffId.toString();
            AuditAction attemptedAction = excludeId != null ? AuditAction.STATUS_CHANGE : AuditAction.CREATE;
            throw new BusinessRuleException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Заявка перетинається з іншою активною відсутністю на цей період",
                    AuditContext.of(attemptedAction, AuditEntityType.LEAVE, entityId, leaveLabel)
            );
        }
    }

    private LeaveType parseLeaveType(String value) {
        try {
            return LeaveType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid leave type: " + value);
        }
    }

    private LeaveStatus parseLeaveStatus(String value) {
        try {
            return LeaveStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid status: " + value);
        }
    }

    private void applyApproval(LeaveRequest leave, UUID currentUserId) {
        Staff approver = staffRepository.findByAuthUserIdAndDeletedAtIsNull(currentUserId.toString()).orElse(null);
        leave.setApprovedBy(approver);
        leave.setApprovedAt(Instant.now());
    }

    private boolean isAutoApproveEligible(UUID currentUserId, Staff targetStaff) {
        Staff currentStaff = staffRepository.findByAuthUserIdAndDeletedAtIsNull(currentUserId.toString()).orElse(null);
        if (currentStaff == null) {
            return false;
        }
        if (currentStaff.getRole() == UserRole.OWNER) {
            return true;
        }

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return staffRepository.countByDeletedAtIsNull() <= 1;
    }

    private LeaveRequest requireLeave(UUID id) {
        return leaveRequestRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Leave request not found: " + id));
    }
}
