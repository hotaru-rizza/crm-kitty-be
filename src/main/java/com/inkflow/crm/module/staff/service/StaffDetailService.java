package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.location.dto.LocationDto;
import com.inkflow.crm.module.staff.dto.StaffDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffDetailService {

    private final StaffLookup staffLookup;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public StaffDetailDto getDetail(UUID staffId) {
        return buildDetailDto(staffLookup.requireStaff(staffId));
    }

    private StaffDetailDto buildDetailDto(Staff staff) {
        List<LocationDto> locations = staff.getLocations().stream()
                .filter(location -> location.getDeletedAt() == null)
                .map(location -> LocationDto.builder()
                        .id(location.getId())
                        .name(location.getName())
                        .address(location.getAddress())
                        .color(location.getColor())
                        .isActive(location.getIsActive())
                        .build())
                .toList();

        List<StaffDetailDto.ScheduleDto> schedule = staff.getSchedules().stream()
                .map(entry -> StaffDetailDto.ScheduleDto.builder()
                        .dayOfWeek(entry.getDayOfWeek().getValue())
                        .isWorking(entry.getIsWorking())
                        .startTime(entry.getStartTime() != null ? entry.getStartTime().toString() : null)
                        .endTime(entry.getEndTime() != null ? entry.getEndTime().toString() : null)
                        .build())
                .toList();

        return StaffDetailDto.builder()
                .id(staff.getId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .email(staff.getEmail())
                .phone(staff.getPhone())
                .avatar(staff.getAvatar())
                .role(staff.getRole().getValue())
                .calendarColor(staff.getCalendarColor())
                .specialization(new ArrayList<>(staff.getSpecialization()))
                .portfolioImages(new ArrayList<>(staff.getPortfolioImages()))
                .bio(staff.getBio())
                .status(staff.getStatus().getValue())
                .accountStatus(staff.getAccountStatus().getValue())
                .locations(locations)
                .schedule(schedule)
                .stats(calculateStats(staff))
                .salaryType(staff.getSalaryType() != null ? staff.getSalaryType().getValue() : "none")
                .salaryRate(staff.getSalaryRate())
                .bankDetails(staff.getBankDetails())
                .isServiceProvider(staff.getIsServiceProvider())
                .instagram(staff.getInstagram())
                .hourlyRate(staff.getHourlyRate())
                .dontDoList(new ArrayList<>(staff.getDontDoList()))
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
                staff.getId(), List.of(AppointmentStatus.SCHEDULED), Instant.now());

        return StaffDetailDto.StaffStatsDto.builder()
                .appointmentsThisMonth(monthAppointments.size())
                .upcomingAppointments(upcoming.size())
                .build();
    }
}
