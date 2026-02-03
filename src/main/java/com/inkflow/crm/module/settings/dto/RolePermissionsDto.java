package com.inkflow.crm.module.settings.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RolePermissionsDto {
    private String role;
    private List<String> permissions;
}
