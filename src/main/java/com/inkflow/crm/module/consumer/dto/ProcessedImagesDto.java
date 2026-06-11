package com.inkflow.crm.module.consumer.dto;

public record ProcessedImagesDto(
        String compositeDataUri,
        String maskDataUri,
        String originalBodyDataUri,
        int width,
        int height
) {
}
