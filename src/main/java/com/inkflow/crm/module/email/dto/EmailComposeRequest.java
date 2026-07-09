package com.inkflow.crm.module.email.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record EmailComposeRequest(
        @NotBlank String subject,
        @NotBlank String body,
        boolean html,
        UUID locationId) {
}
