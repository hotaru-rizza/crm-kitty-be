package com.inkflow.crm.module.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTemplateRequest(
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String body
) {}
