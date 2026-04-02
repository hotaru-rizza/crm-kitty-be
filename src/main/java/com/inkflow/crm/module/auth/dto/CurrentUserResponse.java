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
    private List<String> permissions;
}
