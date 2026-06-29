package com.inkflow.crm.module.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OnboardingRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50)
    private String lastName;

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100)
    private String companyName;

    private String teamSize;

    private String companySize;

    private String instagram;
}
