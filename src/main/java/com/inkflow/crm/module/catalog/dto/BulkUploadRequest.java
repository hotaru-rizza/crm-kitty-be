package com.inkflow.crm.module.catalog.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkUploadRequest(@NotEmpty List<String> imageUrls) {
}
