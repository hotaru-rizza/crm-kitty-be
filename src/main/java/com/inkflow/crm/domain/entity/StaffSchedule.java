package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.DayOfWeek;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "staff_schedules",
       uniqueConstraints = @UniqueConstraint(columnNames = {"staff_id", "day_of_week"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "is_working", nullable = false)
    private Boolean isWorking = true;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    public boolean isWorkingAt(LocalTime time) {
        if (!isWorking || startTime == null || endTime == null) {
            return false;
        }
        return !time.isBefore(startTime) && time.isBefore(endTime);
    }
}
