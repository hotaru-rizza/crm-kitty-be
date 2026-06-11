package com.inkflow.crm.module.storage.dto;

import java.util.List;
import java.util.Map;

public record PresignedUploadResult(
        String key,
        String uploadUrl,
        String method,
        Map<String, List<String>> headers,
        String fileUrl
) {
}
