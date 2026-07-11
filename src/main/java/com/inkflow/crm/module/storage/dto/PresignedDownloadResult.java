package com.inkflow.crm.module.storage.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class PresignedDownloadResult {
    String key;
    String downloadUrl;
    Instant expiresAt;
}
