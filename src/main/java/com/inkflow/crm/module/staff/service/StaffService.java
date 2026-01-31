package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.DayOfWeek;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.location.dto.LocationDto;
import com.inkflow.crm.module.staff.dto.*;
import com.inkflow.crm.module.staff.mapper.StaffMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final StaffScheduleRepository staffScheduleRepository;
    private final StaffInviteRepository staffInviteRepository;
    private final LocationRepository locationRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffMapper staffMapper;

    @Transactional(readOnly = true)
    public List<StaffDto> getAllStaff(PageRequest pageRequest, String role) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Staff> page;

        if (role != null) {
            page = staffRepository.findByTenantIdAndRoleAndDeletedAtIsNull(
                    tenantId, com.inkflow.crm.domain.enums.UserRole.fromValue(role), pageRequest.toPageable());
        } else {
            page = staffRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        }

        return staffMapper.toDtoList(page.getContent());
    }

    public PaginationDto getPagination(PageRequest pageRequest) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Staff> page = staffRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        return PaginationDto.from(page);
    }

    @Transactional(readOnly = true)
    public StaffDetailDto getStaffById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));
        return buildDetailDto(staff);
    }

    @Transactional
    public StaffDto createStaff(CreateStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(request.getEmail());
        }

        Staff staff = staffMapper.toEntity(request);
        staff.setTenantId(tenantId);

        Set<Location> locations = new HashSet<>(locationRepository.findAllById(request.getLocationIds()));
        staff.setLocations(locations);

        staff = staffRepository.save(staff);
        return staffMapper.toDto(staff);
    }

    @Transactional
    public StaffDto updateStaff(UUID id, UpdateStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));

        if (request.getEmail() != null && !request.getEmail().equals(staff.getEmail())) {
            if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
                throw BusinessRuleException.emailAlreadyExists(request.getEmail());
            }
        }

        staffMapper.updateEntity(request, staff);

        if (request.getLocationIds() != null) {
            Set<Location> locations = new HashSet<>(locationRepository.findAllById(request.getLocationIds()));
            staff.setLocations(locations);
        }

        staff = staffRepository.save(staff);
        return staffMapper.toDto(staff);
    }

    @Transactional
    public void deleteStaff(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));

        staff.softDelete();
        staffRepository.save(staff);
    }

    @Transactional
    public void updateSchedule(UUID staffId, UpdateScheduleRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        staffScheduleRepository.deleteByStaffId(staffId);

        List<StaffSchedule> schedules = request.getSchedule().stream()
                .map(entry -> StaffSchedule.builder()
                        .staff(staff)
                        .dayOfWeek(DayOfWeek.fromValue(entry.getDayOfWeek()))
                        .isWorking(entry.getIsWorking())
                        .startTime(entry.getStartTime() != null ? LocalTime.parse(entry.getStartTime()) : null)
                        .endTime(entry.getEndTime() != null ? LocalTime.parse(entry.getEndTime()) : null)
                        .build())
                .collect(Collectors.toList());

        staffScheduleRepository.saveAll(schedules);
    }

    @Transactional
    public String inviteStaff(InviteStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(request.getEmail());
        }

        if (staffInviteRepository.existsByEmailAndTenantIdAndAcceptedAtIsNull(request.getEmail(), tenantId)) {
            throw new BusinessRuleException("Invite already pending for this email");
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(7));

        StaffInvite invite = StaffInvite.builder()
                .tenantId(tenantId)
                .email(request.getEmail())
                .role(com.inkflow.crm.domain.enums.UserRole.fromValue(request.getRole()))
                .calendarColor(request.getCalendarColor())
                .token(token)
                .expiresAt(expiresAt)
                .invitedBy(currentUserId)
                .locationIds(new HashSet<>(request.getLocationIds()))
                .build();

        staffInviteRepository.save(invite);
        return token;
    }

    @Transactional
    public StaffDto acceptInvite(AcceptInviteRequest request) {
        StaffInvite invite = staffInviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BusinessRuleException("Invalid invite token"));

        if (invite.isExpired()) {
            throw new BusinessRuleException("Invite has expired");
        }

        if (invite.isAccepted()) {
            throw new BusinessRuleException("Invite has already been accepted");
        }

        Staff staff = Staff.builder()
                .tenantId(invite.getTenantId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(invite.getEmail())
                .phone(request.getPhone())
                .role(invite.getRole())
                .calendarColor(invite.getCalendarColor())
                .status(com.inkflow.crm.domain.enums.StaffStatus.WORKING)
                .build();

        Set<Location> locations = new HashSet<>(locationRepository.findAllById(invite.getLocationIds()));
        staff.setLocations(locations);

        staff = staffRepository.save(staff);

        invite.setAcceptedAt(Instant.now());
        staffInviteRepository.save(invite);

        return staffMapper.toDto(staff);
    }

    private StaffDetailDto buildDetailDto(Staff staff) {
        List<LocationDto> locations = staff.getLocations().stream()
                .filter(l -> l.getDeletedAt() == null)
                .map(l -> LocationDto.builder()
                        .id(l.getId())
                        .name(l.getName())
                        .address(l.getAddress())
                        .color(l.getColor())
                        .isActive(l.getIsActive())
                        .build())
                .collect(Collectors.toList());

        List<StaffDetailDto.ScheduleDto> schedule = staff.getSchedules().stream()
                .map(s -> StaffDetailDto.ScheduleDto.builder()
                        .dayOfWeek(s.getDayOfWeek().getValue())
                        .isWorking(s.getIsWorking())
                        .startTime(s.getStartTime() != null ? s.getStartTime().toString() : null)
                        .endTime(s.getEndTime() != null ? s.getEndTime().toString() : null)
                        .build())
                .collect(Collectors.toList());

        StaffDetailDto.StaffStatsDto stats = calculateStats(staff);

        return StaffDetailDto.builder()
                .id(staff.getId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .email(staff.getEmail())
                .phone(staff.getPhone())
                .avatar(staff.getAvatar())
                .role(staff.getRole().getValue())
                .calendarColor(staff.getCalendarColor())
                .specialization(staff.getSpecialization())
                .bio(staff.getBio())
                .status(staff.getStatus().getValue())
                .locations(locations)
                .schedule(schedule)
                .stats(stats)
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }

    private StaffDetailDto.StaffStatsDto calculateStats(Staff staff) {
        UUID tenantId = staff.getTenantId();
        LocalDate now = LocalDate.now();
        Instant monthStart = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthEnd = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<Appointment> monthAppointments = appointmentRepository.findByTenantIdAndArtistIdAndDateRange(
                tenantId, staff.getId(), monthStart, monthEnd);

        List<Appointment> upcoming = appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                staff.getId(), List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED), Instant.now());

        return StaffDetailDto.StaffStatsDto.builder()
                .appointmentsThisMonth(monthAppointments.size())
                .upcomingAppointments(upcoming.size())
                .build();
    }
}
