package com.inkflow.crm.module.onboarding.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OnboardingServiceDraftDto {

    @NotBlank(message = "Service title is required")
    @Size(min = 2, max = 100, message = "Service title must be between 2 and 100 characters")
    private String title;

    @NotNull(message = "Service duration is required")
    @Min(value = 15, message = "Service duration must be at least 15 minutes")
    private Integer duration;

    @NotNull(message = "Service price is required")
    @DecimalMin(value = "0.0", message = "Service price must be positive")
    private BigDecimal price;
}
