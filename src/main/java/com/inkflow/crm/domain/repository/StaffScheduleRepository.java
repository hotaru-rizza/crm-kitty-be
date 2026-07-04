package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, UUID> {
    List<StaffSchedule> findByStaffId(UUID staffId);
    Optional<StaffSchedule> findByStaffIdAndDayOfWeek(UUID staffId, DayOfWeek dayOfWeek);
    List<StaffSchedule> findByStaffIdIn(List<UUID> staffIds);
}
