package com.inkflow.crm.module.staff.dto;

import jakarta.validation.constraints.*;
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
public class InviteStaffRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(admin|artist)$", message = "Role must be admin or artist")
    private String role;

    private String calendarColor;

    @Builder.Default
    private Boolean isServiceProvider = true;

    @NotEmpty(message = "At least one location is required")
    private List<UUID> locationIds;
}
