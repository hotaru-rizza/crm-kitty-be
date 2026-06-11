package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.DayOfWeek;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.staff.dto.UpdateScheduleRequest;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffScheduleService {

    private final StaffLookup staffLookup;
    private final StaffRepository staffRepository;
    private final EntityManager entityManager;

    @Transactional
    public void updateSchedule(UUID staffId, UpdateScheduleRequest request) {
        Staff staff = staffLookup.requireStaff(staffId);

        staff.getSchedules().clear();
        entityManager.flush();

        request.getSchedule().forEach(entry -> {
            StaffSchedule schedule = StaffSchedule.builder()
                    .staff(staff)
                    .dayOfWeek(DayOfWeek.fromValue(entry.getDayOfWeek()))
                    .isWorking(entry.getIsWorking())
                    .startTime(entry.getStartTime() != null ? LocalTime.parse(entry.getStartTime()) : null)
                    .endTime(entry.getEndTime() != null ? LocalTime.parse(entry.getEndTime()) : null)
                    .build();
            staff.getSchedules().add(schedule);
        });

        staffRepository.save(staff);
        log.info("Schedule updated for staffId={} tenantId={}", staffId, staff.getTenantId());
    }
}
