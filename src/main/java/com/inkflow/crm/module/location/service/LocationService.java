package com.inkflow.crm.module.location.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.location.dto.*;
import com.inkflow.crm.module.location.mapper.LocationMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
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
public class LocationService {

    private final LocationRepository locationRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final TransactionRepository transactionRepository;
    private final LocationMapper locationMapper;

    @Transactional(readOnly = true)
    public PageResult<LocationDto> getAllLocations(PageRequest pageRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Location> page = locationRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        List<LocationDto> data = page.getContent().stream()
                .map(this::mapWithStaffCount)
                .toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public LocationDetailDto getLocationById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
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

        location = locationRepository.save(location);
        return mapWithStaffCount(location);
    }

    @Transactional
    public LocationDto updateLocation(UUID id, UpdateLocationRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));

        locationMapper.updateEntity(request, location);
        location = locationRepository.save(location);
        return mapWithStaffCount(location);
    }

    @Transactional
    public void deleteLocation(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));

        location.softDelete();
        locationRepository.save(location);
    }

    @Transactional
    public void assignStaff(UUID locationId, AssignStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.location(locationId.toString()));

        List<Staff> staffList = staffRepository.findAllById(request.getStaffIds());
        location.setStaff(new HashSet<>(staffList));
        locationRepository.save(location);
    }

    private LocationDto mapWithStaffCount(Location location) {
        return LocationDto.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .phone(location.getPhone())
                .googleMapsLink(location.getGoogleMapsLink())
                .color(location.getColor())
                .isActive(location.getIsActive())
                .staffCount((int) location.getStaff().stream().filter(s -> s.getDeletedAt() == null).count())
                .createdAt(location.getCreatedAt())
                .build();
    }

    private LocationDetailDto.LocationStatsDto calculateStats(Location location) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        LocalDate now = LocalDate.now();
        Instant monthStart = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthEnd = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long appointmentsCount = appointmentRepository.countByTenantIdAndLocationIdAndStartTimeBetweenAndDeletedAtIsNull(
                tenantId, location.getId(), monthStart, monthEnd);

        BigDecimal revenue = transactionRepository.sumRevenueByLocationAndDateRange(
                tenantId, location.getId(), monthStart, monthEnd);

        return LocationDetailDto.LocationStatsDto.builder()
                .appointmentsThisMonth((int) appointmentsCount)
                .revenueThisMonth(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }
}
