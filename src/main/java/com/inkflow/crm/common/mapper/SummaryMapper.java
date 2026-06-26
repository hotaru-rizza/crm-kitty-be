package com.inkflow.crm.common.mapper;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.client.dto.ClientSummaryDto;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
import org.springframework.stereotype.Component;

@Component
public class SummaryMapper {

    public ClientSummaryDto toClientSummary(Client client) {
        return ClientSummaryDto.builder()
                .id(client.getId())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .phone(client.getPhone())
                .avatar(client.getAvatar())
                .blacklisted(client.isBlacklisted())
                .hasMedicalConditions(client.hasMedicalConditions())
                .build();
    }

    public StaffSummaryDto toStaffSummary(Staff staff) {
        return StaffSummaryDto.builder()
                .id(staff.getId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .avatar(staff.getAvatar())
                .calendarColor(staff.getCalendarColor())
                .role(staff.getRole().getValue())
                .build();
    }
}
