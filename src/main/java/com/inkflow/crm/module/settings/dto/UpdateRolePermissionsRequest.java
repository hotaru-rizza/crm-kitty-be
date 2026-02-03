package com.inkflow.crm.module.settings.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRolePermissionsRequest {
    @NotNull
    private List<String> permissions;
}
