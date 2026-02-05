package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, UUID> {
    List<StaffSchedule> findByStaffId(UUID staffId);
    Optional<StaffSchedule> findByStaffIdAndDayOfWeek(UUID staffId, DayOfWeek dayOfWeek);
    
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StaffSchedule s WHERE s.staff.id = :staffId")
    void deleteByStaffId(@Param("staffId") UUID staffId);
}
