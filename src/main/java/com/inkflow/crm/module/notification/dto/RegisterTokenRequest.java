package com.inkflow.crm.module.notification.dto;

import com.inkflow.crm.module.notification.entity.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterTokenRequest(
        @NotBlank String token,
        @NotNull DeviceToken.Platform platform
) {}
