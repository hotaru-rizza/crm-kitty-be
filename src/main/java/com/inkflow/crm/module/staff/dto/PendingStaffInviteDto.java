package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingStaffInviteDto {
    private UUID id;
    private String email;
    private String role;
    private Instant expiresAt;
    private Instant createdAt;
    private boolean expired;
}
