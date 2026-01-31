package com.inkflow.crm.module.location.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignStaffRequest {

    @NotEmpty(message = "Staff IDs are required")
    private List<UUID> staffIds;
}
