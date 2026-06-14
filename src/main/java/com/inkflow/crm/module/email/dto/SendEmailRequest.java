package com.inkflow.crm.module.email.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record SendEmailRequest(
        List<UUID> clientIds,
        List<UUID> staffIds,
        @NotBlank String subject,
        @NotBlank String body) {

    public List<UUID> clientIds() {
        return clientIds == null ? List.of() : clientIds;
    }

    public List<UUID> staffIds() {
        return staffIds == null ? List.of() : staffIds;
    }

    @AssertTrue(message = "At least one recipient is required")
    public boolean isRecipientsPresent() {
        return !clientIds().isEmpty() || !staffIds().isEmpty();
    }
}
