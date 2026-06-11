package com.inkflow.crm.module.consumer.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveGenerationRequest(
        @NotBlank String imageDataUri,
        @NotBlank String prompt
) {
}
