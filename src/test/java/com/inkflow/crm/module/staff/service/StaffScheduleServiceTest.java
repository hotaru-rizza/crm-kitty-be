package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.staff.dto.UpdateScheduleRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffScheduleServiceTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private EntityManager entityManager;

    @Captor
    private ArgumentCaptor<Staff> staffCaptor;

    @InjectMocks
    private StaffScheduleService staffScheduleService;

    @Test
    void shouldReplaceExistingEntriesWhenUpdatingSchedule() {
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).tenantId(UUID.randomUUID()).build();
        staff.setSchedules(new HashSet<>(List.of(
                StaffSchedule.builder()
                        .dayOfWeek(com.inkflow.crm.domain.enums.DayOfWeek.MONDAY)
                        .isWorking(true)
                        .build()
        )));

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        UpdateScheduleRequest request = UpdateScheduleRequest.builder()
                .schedule(List.of(
                        UpdateScheduleRequest.ScheduleEntry.builder()
                                .dayOfWeek("tuesday")
                                .isWorking(true)
                                .startTime("10:00")
                                .endTime("18:00")
                                .build()
                ))
                .build();

        staffScheduleService.updateSchedule(staffId, request);

        verify(entityManager).flush();
        verify(staffRepository).save(staffCaptor.capture());

        Staff saved = staffCaptor.getValue();
        assertEquals(1, saved.getSchedules().size());
        StaffSchedule entry = saved.getSchedules().iterator().next();
        assertEquals(com.inkflow.crm.domain.enums.DayOfWeek.TUESDAY, entry.getDayOfWeek());
        assertTrue(entry.getIsWorking());
        assertEquals(java.time.LocalTime.of(10, 0), entry.getStartTime());
        assertEquals(java.time.LocalTime.of(18, 0), entry.getEndTime());
        assertEquals(staff, entry.getStaff());
    }

    @Test
    void shouldPersistDayOffWithoutTimesWhenScheduleEntryNotWorking() {
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).tenantId(UUID.randomUUID()).build();
        staff.setSchedules(new HashSet<>());
        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        UpdateScheduleRequest request = UpdateScheduleRequest.builder()
                .schedule(List.of(
                        UpdateScheduleRequest.ScheduleEntry.builder()
                                .dayOfWeek("sunday")
                                .isWorking(false)
                                .build()
                ))
                .build();

        staffScheduleService.updateSchedule(staffId, request);

        verify(staffRepository).save(staffCaptor.capture());
        StaffSchedule entry = staffCaptor.getValue().getSchedules().iterator().next();
        assertEquals(com.inkflow.crm.domain.enums.DayOfWeek.SUNDAY, entry.getDayOfWeek());
        assertFalse(entry.getIsWorking());
        assertNull(entry.getStartTime());
        assertNull(entry.getEndTime());
    }

    @Test
    void shouldClearAllDaysWhenUpdatingWithEmptySchedule() {
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).tenantId(UUID.randomUUID()).build();
        staff.setSchedules(new HashSet<>(List.of(
                StaffSchedule.builder()
                        .dayOfWeek(com.inkflow.crm.domain.enums.DayOfWeek.FRIDAY)
                        .isWorking(true)
                        .build()
        )));
        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        UpdateScheduleRequest request = UpdateScheduleRequest.builder()
                .schedule(List.of())
                .build();

        staffScheduleService.updateSchedule(staffId, request);

        verify(staffRepository).save(staffCaptor.capture());
        assertTrue(staffCaptor.getValue().getSchedules().isEmpty());
    }
}
