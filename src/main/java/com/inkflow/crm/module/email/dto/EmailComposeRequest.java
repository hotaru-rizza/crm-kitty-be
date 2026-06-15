package com.inkflow.crm.module.email.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailComposeRequest(
        @NotBlank String subject,
        @NotBlank String body,
        boolean html) {
}
