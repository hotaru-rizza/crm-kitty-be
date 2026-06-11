package com.inkflow.crm.module.consumer.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateRequest(
        @NotBlank String prompt,
        String style,
        String colorMode,
        String background,
        String ratio,
        String bodyImage
) {
}
