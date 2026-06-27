package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.dto.AuditContext;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.staff.dto.CreateStaffRequest;
import com.inkflow.crm.module.staff.dto.StaffDto;
import com.inkflow.crm.module.staff.dto.UpdateStaffRequest;
import com.inkflow.crm.module.staff.mapper.StaffMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService {

    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;
    private final StaffMapper staffMapper;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public PageResult<StaffDto> getAllStaff(
            PageRequest pageRequest,
            String search,
            String role,
            UUID locationId,
            String accountStatus) {
        Page<Staff> page = getStaffPage(pageRequest, search, role, locationId, accountStatus);
        return new PageResult<>(staffMapper.toDtoList(page.getContent()), PaginationDto.from(page));
    }

    @Transactional
    public StaffDto createStaff(CreateStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(request.getEmail());
        }

        Staff staff = staffMapper.toEntity(request);
        staff.setTenantId(tenantId);
        staff.setLocations(resolveLocations(request.getLocationIds()));

        staff = staffRepository.save(staff);
        log.info("Staff created: tenantId={} staffId={}", tenantId, staff.getId());
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.STAFF,
                staff.getId().toString(),
                staff.getFullName()
        );
        return staffMapper.toDto(staff);
    }

    @Transactional
    public StaffDto updateStaff(UUID id, UpdateStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = requireStaff(id, tenantId);

        if (request.getEmail() != null && !request.getEmail().equals(staff.getEmail())) {
            if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
                throw new BusinessRuleException(
                        ErrorCode.EMAIL_ALREADY_EXISTS,
                        "Email already exists: " + request.getEmail(),
                        AuditContext.of(AuditAction.UPDATE, AuditEntityType.STAFF, id.toString(), staff.getFullName())
                );
            }
        }

        staffMapper.updateEntity(request, staff);

        if (request.getLocationIds() != null) {
            staff.setLocations(resolveLocations(request.getLocationIds()));
        }

        staff = staffRepository.save(staff);
        log.info("Staff updated: tenantId={} staffId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.STAFF,
                id.toString(),
                staff.getFullName()
        );
        return staffMapper.toDto(staff);
    }

    @Transactional
    public void deleteStaff(UUID id) {
        SecurityUtils.requireOwner();

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = requireStaff(id, tenantId);

        staff.softDelete();
        staffRepository.save(staff);
        log.info("Staff deleted: tenantId={} staffId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.STAFF_DEACTIVATE,
                AuditEntityType.STAFF,
                id.toString(),
                staff.getFullName()
        );
    }

    private Staff requireStaff(UUID id, UUID tenantId) {
        return staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));
    }

    private Set<Location> resolveLocations(Collection<UUID> locationIds) {
        return new HashSet<>(locationRepository.findAllById(locationIds));
    }

    private Page<Staff> getStaffPage(
            PageRequest pageRequest,
            String search,
            String role,
            UUID locationId,
            String accountStatus) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole userRole = role != null ? UserRole.fromValue(role) : null;
        AccountStatus accStatus = accountStatus != null ? AccountStatus.fromValue(accountStatus) : null;

        return staffRepository.findWithFilters(tenantId, search, userRole, locationId, accStatus, pageRequest.toPageable());
    }
}
