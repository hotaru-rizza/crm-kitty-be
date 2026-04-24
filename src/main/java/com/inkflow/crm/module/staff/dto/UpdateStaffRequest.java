package com.inkflow.crm.module.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStaffRequest {

    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    private String firstName;

    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    private String lastName;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String avatar;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code")
    private String calendarColor;

    private List<String> specialization;
    private List<String> portfolioImages;
    private String bio;
    private List<UUID> locationIds;

    @Pattern(regexp = "^(working|on_vacation|sick_leave|fired)$", message = "Invalid status")
    private String status;

    @Pattern(regexp = "^(none|fixed|percent)$", message = "Invalid salary type")
    private String salaryType;

    @jakarta.validation.constraints.DecimalMin(value = "0.0")
    private BigDecimal salaryRate;

    private String bankDetails;
    private String position;
    private LocalDate birthday;
    private String taxId;
    private String iban;
    private String bankCard;
    private Boolean availableForOnlineBooking;
}
