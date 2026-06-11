package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.enums.LeaveType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason")
    private String reason;

    @Column(name = "notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Staff approvedBy;

    @Column(name = "approved_at")
    private java.time.Instant approvedAt;

    public int getDaysCount() {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }

    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return status == LeaveStatus.APPROVED &&
               !today.isBefore(startDate) &&
               !today.isAfter(endDate);
    }

    public boolean coversDate(LocalDate date) {
        return status == LeaveStatus.APPROVED &&
               !date.isBefore(startDate) &&
               !date.isAfter(endDate);
    }
}
