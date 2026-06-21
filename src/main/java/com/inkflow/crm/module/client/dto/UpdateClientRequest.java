package com.inkflow.crm.module.client.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClientRequest {

    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    private String firstName;

    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    private String lastName;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String phone;

    private String email;
    private LocalDate birthDate;
    private String instagram;
    private String telegram;
    private String whatsapp;
    private String facebook;
    private List<String> tags;
    private List<String> medicalConditions;
    private String notes;

    @Pattern(regexp = "^(active|inactive|blacklisted)$", message = "Invalid status")
    private String status;
}
