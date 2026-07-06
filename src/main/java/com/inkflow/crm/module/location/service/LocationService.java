package com.inkflow.crm.module.location.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.location.dto.*;
import com.inkflow.crm.module.location.mapper.LocationMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationRepository locationRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final TransactionRepository transactionRepository;
    private final LocationMapper locationMapper;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    @Transactional(readOnly = true)
    public PageResult<LocationDto> getAllLocations(PageRequest pageRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Location> page = locationRepository.findByDeletedAtIsNull( pageRequest.toPageable());
        List<LocationDto> data = page.getContent().stream()
                .map(locationMapper::toDtoWithStaffCount)
                .toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public LocationDetailDto getLocationById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));

        LocationDetailDto.LocationStatsDto stats = calculateStats(location);
        return locationMapper.toDetailDto(location, stats);
    }

    @Transactional
    public LocationDto createLocation(CreateLocationRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationMapper.toEntity(request);
        location.setTenantId(tenantId);
        if (locationRepository.findByIsDefaultTrueAndDeletedAtIsNull().isEmpty()) {
            location.setIsDefault(true);
        }

        location = locationRepository.save(location);
        log.info("Location created: tenantId={} locationId={}", tenantId, location.getId());
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.LOCATION,
                location.getId().toString(),
                auditLabelFormatter.location(location.getName())
        );
        return locationMapper.toDtoWithStaffCount(location);
    }

    @Transactional
    public LocationDto updateLocation(UUID id, UpdateLocationRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));

        locationMapper.updateEntity(request, location);
        ensureCanChangeActiveStatus(tenantId, location, request.getIsActive());
        location = locationRepository.save(location);
        log.info("Location updated: tenantId={} locationId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.LOCATION,
                location.getId().toString(),
                auditLabelFormatter.location(location.getName())
        );
        return locationMapper.toDtoWithStaffCount(location);
    }

    @Transactional
    public void deleteLocation(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));

        ensureCanDeleteLocation(tenantId, location);

        location.softDelete();
        locationRepository.save(location);
        log.info("Location deleted: tenantId={} locationId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.LOCATION,
                location.getId().toString(),
                auditLabelFormatter.location(location.getName())
        );
    }

    @Transactional
    public void assignStaff(UUID locationId, AssignStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ResourceNotFoundException.location(locationId.toString()));

        List<Staff> staffList = staffRepository.findByIdInAndDeletedAtIsNull(request.getStaffIds());
        location.setStaff(new HashSet<>(staffList));
        locationRepository.save(location);
        log.info("Staff assigned to location: tenantId={} locationId={} count={}", tenantId, locationId, staffList.size());
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.LOCATION,
                location.getId().toString(),
                auditLabelFormatter.location(location.getName()),
                null,
                staffList.size() + " майстрів"
        );
    }

    @Transactional
    public LocationDto setDefaultLocation(UUID id) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));

        locationRepository.findByIsDefaultTrueAndDeletedAtIsNull()
                .filter(current -> !current.getId().equals(id))
                .ifPresent(current -> {
                    current.setIsDefault(false);
                    locationRepository.save(current);
                });

        location.setIsDefault(true);
        location = locationRepository.save(location);
        log.info("Default location set: tenantId={} locationId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.LOCATION,
                location.getId().toString(),
                auditLabelFormatter.location(location.getName()),
                null,
                "Основна локація"
        );
        return locationMapper.toDtoWithStaffCount(location);
    }

    private void ensureCanDeleteLocation(UUID tenantId, Location location) {
        if (Boolean.TRUE.equals(location.getIsDefault())) {
            throw BusinessRuleException.defaultLocationCannotBeDeleted();
        }
        if (locationRepository.countByDeletedAtIsNull() <= 1) {
            throw BusinessRuleException.lastLocationCannotBeDeleted();
        }
    }

    private void ensureCanChangeActiveStatus(UUID tenantId, Location location, Boolean requestedActive) {
        if (!Boolean.FALSE.equals(requestedActive) || !Boolean.TRUE.equals(location.getIsActive())) {
            return;
        }

        if (locationRepository.countByIsActiveAndDeletedAtIsNull( true) <= 1) {
            throw BusinessRuleException.lastActiveLocationCannotBeDeactivated();
        }
    }

    private LocationDetailDto.LocationStatsDto calculateStats(Location location) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        LocalDate now = LocalDate.now();
        Instant monthStart = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthEnd = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long appointmentsCount = appointmentRepository.countByLocationIdAndStartTimeBetweenAndDeletedAtIsNull( location.getId(), monthStart, monthEnd);

        BigDecimal revenue = transactionRepository.sumRevenueByLocationAndDateRange( location.getId(), monthStart, monthEnd);

        return LocationDetailDto.LocationStatsDto.builder()
                .appointmentsThisMonth((int) appointmentsCount)
                .revenueThisMonth(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }
}
