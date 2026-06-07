package com.inkflow.crm.module.leave.mapper;

import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeaveRequestMapper {

    default LeaveRequestDto toDto(LeaveRequest leave) {
        if (leave == null) return null;

        Staff staff = leave.getStaff();
        Staff approver = leave.getApprovedBy();

        return LeaveRequestDto.builder()
                .id(leave.getId())
                .staffId(staff.getId())
                .staffName(staff.getFullName())
                .staffAvatar(staff.getAvatar())
                .leaveType(leave.getLeaveType().name())
                .status(leave.getStatus().name())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .daysCount(leave.getDaysCount())
                .reason(leave.getReason())
                .notes(leave.getNotes())
                .approvedById(approver != null ? approver.getId() : null)
                .approvedByName(approver != null ? approver.getFullName() : null)
                .approvedAt(leave.getApprovedAt())
                .createdAt(leave.getCreatedAt())
                .build();
    }

    List<LeaveRequestDto> toDtoList(List<LeaveRequest> leaves);
}
