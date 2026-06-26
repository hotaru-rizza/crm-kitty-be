package com.inkflow.crm.module.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSummaryDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private String avatar;
    private boolean blacklisted;
    private boolean hasMedicalConditions;
}
