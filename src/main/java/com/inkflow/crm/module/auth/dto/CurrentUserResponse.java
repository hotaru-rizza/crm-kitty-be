package com.inkflow.crm.module.auth.dto;

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
public class CurrentUserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String avatar;
    private String role;
    private UUID tenantId;
    private String tenantName;
    private List<UUID> locationIds;
    private PermissionsDto permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionsDto {
        private boolean canManageStaff;
        private boolean canManageClients;
        private boolean canManageServices;
        private boolean canManageLocations;
        private boolean canViewFinances;
        private boolean canManageSettings;
    }
}
