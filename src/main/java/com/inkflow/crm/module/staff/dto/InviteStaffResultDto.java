package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteStaffResultDto {
    private String token;
    private boolean resent;
    private boolean emailDispatched;
}
