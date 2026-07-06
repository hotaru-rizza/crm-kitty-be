package com.inkflow.crm.module.leave.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequestDto {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private String staffAvatar;
    private String staffAccountStatus;
    private boolean staffDeleted;
    private String leaveType;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private int daysCount;
    private String reason;
    private String notes;
    private UUID approvedById;
    private String approvedByName;
    private Instant approvedAt;
    private Instant createdAt;
}
