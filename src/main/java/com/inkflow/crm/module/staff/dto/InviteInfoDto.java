package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteInfoDto {
    private String email;
    private String role;
    private Instant expiresAt;
    private boolean expired;
    private boolean accepted;
}
