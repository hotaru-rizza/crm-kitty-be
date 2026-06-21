package com.inkflow.crm.module.client.dto;

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
public class ClientDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String avatar;
    private LocalDate birthDate;
    private String instagram;
    private String telegram;
    private String whatsapp;
    private String facebook;
    private List<String> tags;
    private String status;
    private String notes;
    private Instant lastVisit;
    private Instant firstVisit;
    private Integer totalVisits;
    private Integer cancelledVisits;
    private BigDecimal ltv;
    private BigDecimal balance;
    private Instant createdAt;
}
