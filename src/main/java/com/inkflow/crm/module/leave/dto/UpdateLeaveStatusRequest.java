package com.inkflow.crm.module.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLeaveStatusRequest {

    @NotNull(message = "Status is required")
    private String status; // APPROVED or REJECTED

    private String notes;
}
