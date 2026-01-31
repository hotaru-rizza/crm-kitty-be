package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffSummaryDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String avatar;
    private String calendarColor;
    private String role;
}
