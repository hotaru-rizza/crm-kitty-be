package com.inkflow.crm.module.catalog.dto;

import java.util.List;
import java.util.UUID;

public record TattooDto(
        Long id,
        UUID staffId,
        String status,
        String imageUrl,
        String thumbnailUrl,
        Integer width,
        Integer height,
        String blurHash,
        String dominantColor,
        String authorName,
        String authorUrl,
        String description,
        String altDescription,
        List<String> tags,
        boolean showcase
) {
}
