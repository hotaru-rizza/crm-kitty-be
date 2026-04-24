package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String avatar;
    private String role;
    private String calendarColor;
    private List<String> specialization;
    private List<String> portfolioImages;
    private String status;
    private List<UUID> locationIds;
    private String salaryType;
    private BigDecimal salaryRate;
    private String bankDetails;
    private String position;
    private LocalDate birthday;
    private String taxId;
    private String iban;
    private String bankCard;
    private Boolean availableForOnlineBooking;
    private Instant createdAt;
}
