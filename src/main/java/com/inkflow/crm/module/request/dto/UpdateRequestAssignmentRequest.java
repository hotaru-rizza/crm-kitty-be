package com.inkflow.crm.module.request.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRequestAssignmentRequest {

    /** Null clears the assigned artist. */
    private UUID assignedStaffId;
}
