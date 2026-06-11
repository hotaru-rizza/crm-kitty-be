package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.DayOfWeek;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.staff.dto.StaffDetailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffDetailServiceTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @InjectMocks
    private StaffDetailService staffDetailService;

    @Test
    void shouldBuildStaffProfileWhenStaffExists() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = buildStaff(staffId, tenantId);
        stubEmptyAppointments(staffId, tenantId);

        StaffDetailDto detail = staffDetailService.getDetail(staffId);

        assertEquals(staffId, detail.getId());
        assertEquals("Alex", detail.getFirstName());
        assertEquals("artist", detail.getRole());
        assertEquals(0, detail.getStats().getAppointmentsThisMonth());
        assertEquals(0, detail.getStats().getUpcomingAppointments());
    }

    @Test
    void shouldExcludeDeletedLocationsWhenBuildingDetail() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = buildStaff(staffId, tenantId);

        Location active = Location.builder()
                .id(UUID.randomUUID())
                .name("Studio A")
                .address("1 Main St")
                .color("#fff")
                .isActive(true)
                .build();
        Location deleted = Location.builder()
                .id(UUID.randomUUID())
                .name("Closed Studio")
                .address("2 Old St")
                .color("#000")
                .isActive(false)
                .build();
        deleted.setDeletedAt(Instant.now());
        staff.setLocations(new HashSet<>(List.of(active, deleted)));
        stubEmptyAppointments(staffId, tenantId);

        StaffDetailDto detail = staffDetailService.getDetail(staffId);

        assertEquals(1, detail.getLocations().size());
        assertEquals("Studio A", detail.getLocations().getFirst().getName());
    }

    @Test
    void shouldCountMonthAndUpcomingAppointmentsInStats() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = buildStaff(staffId, tenantId);

        Appointment monthAppointment = Appointment.builder().id(UUID.randomUUID()).build();
        Appointment upcomingAppointment = Appointment.builder().id(UUID.randomUUID()).build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(appointmentRepository.findByTenantIdAndArtistIdAndDateRange(
                eq(tenantId), eq(staffId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(monthAppointment, monthAppointment));
        when(appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                eq(staffId),
                eq(List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED)),
                any(Instant.class)))
                .thenReturn(List.of(upcomingAppointment));

        StaffDetailDto detail = staffDetailService.getDetail(staffId);

        assertEquals(2, detail.getStats().getAppointmentsThisMonth());
        assertEquals(1, detail.getStats().getUpcomingAppointments());

        verify(appointmentRepository).findByTenantIdAndArtistIdAndDateRange(
                eq(tenantId), eq(staffId), instantCaptor.capture(), instantCaptor.capture());
        List<Instant> monthRange = instantCaptor.getAllValues();
        assertEquals(2, monthRange.size());
        assertTrue(monthRange.getFirst().isBefore(monthRange.get(1)));
    }

    @Test
    void shouldDefaultSalaryTypeToNoneWhenNull() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = buildStaff(staffId, tenantId);
        staff.setSalaryType(null);
        stubEmptyAppointments(staffId, tenantId);

        StaffDetailDto detail = staffDetailService.getDetail(staffId);

        assertEquals("none", detail.getSalaryType());
    }

    @Test
    void shouldMapScheduleWithNullTimesWhenDayOff() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Staff staff = buildStaff(staffId, tenantId);
        staff.setSchedules(new HashSet<>(List.of(
                StaffSchedule.builder()
                        .dayOfWeek(DayOfWeek.SUNDAY)
                        .isWorking(false)
                        .startTime(null)
                        .endTime(null)
                        .build()
        )));
        stubEmptyAppointments(staffId, tenantId);

        StaffDetailDto detail = staffDetailService.getDetail(staffId);

        assertEquals(1, detail.getSchedule().size());
        assertEquals("sunday", detail.getSchedule().getFirst().getDayOfWeek());
        assertNull(detail.getSchedule().getFirst().getStartTime());
        assertNull(detail.getSchedule().getFirst().getEndTime());
    }

    private Staff buildStaff(UUID staffId, UUID tenantId) {
        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .firstName("Alex")
                .lastName("Ink")
                .email("alex@test.com")
                .role(UserRole.ARTIST)
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .calendarColor("#6366f1")
                .build();
        staff.setLocations(new HashSet<>());
        staff.setSchedules(new HashSet<>(List.of(
                StaffSchedule.builder()
                        .dayOfWeek(DayOfWeek.MONDAY)
                        .isWorking(true)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .build()
        )));
        staff.setSpecialization(new HashSet<>());
        staff.setPortfolioImages(new HashSet<>());
        staff.setDontDoList(new HashSet<>());
        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        return staff;
    }

    private void stubEmptyAppointments(UUID staffId, UUID tenantId) {
        when(appointmentRepository.findByTenantIdAndArtistIdAndDateRange(
                eq(tenantId), eq(staffId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                eq(staffId),
                eq(List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED)),
                any(Instant.class)))
                .thenReturn(List.of());
    }
}
