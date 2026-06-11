package com.inkflow.crm.module.consumer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TryOnRequest(
        @NotBlank String bodyImage,
        @NotBlank String sketchImage,
        @NotNull @Valid PlacementDto placement
) {
}
