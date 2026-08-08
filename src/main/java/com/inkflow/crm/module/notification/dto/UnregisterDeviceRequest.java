package com.inkflow.crm.module.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnregisterDeviceRequest(
        @NotBlank @Size(max = 4096) String token
) {
}
