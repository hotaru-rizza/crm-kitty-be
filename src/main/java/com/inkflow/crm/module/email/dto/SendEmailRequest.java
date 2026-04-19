package com.inkflow.crm.module.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailRequest {
    @NotEmpty
    private List<UUID> clientIds;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;
}
