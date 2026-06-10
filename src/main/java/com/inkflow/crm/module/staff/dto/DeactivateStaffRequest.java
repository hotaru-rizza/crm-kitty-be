package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateStaffRequest {
    private boolean cancelFutureAppointments;
}
