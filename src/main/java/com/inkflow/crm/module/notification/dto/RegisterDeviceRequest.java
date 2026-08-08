package com.inkflow.crm.module.notification.dto;

import com.inkflow.crm.module.notification.entity.DeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank @Size(max = 4096) String token,
        @NotNull DeviceToken.Platform platform,
        @Size(max = 64) String appVersion
) {
}
