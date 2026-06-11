package com.inkflow.crm.module.catalog.dto;

import java.util.List;

public record TattooStyleDto(
        Long id,
        String slug,
        String name,
        String imageUrl,
        List<String> imageUrls
) {
}
