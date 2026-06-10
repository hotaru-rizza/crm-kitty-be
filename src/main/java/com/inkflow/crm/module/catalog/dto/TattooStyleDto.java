package com.inkflow.crm.module.catalog.dto;

import com.inkflow.crm.module.catalog.entity.TattooStyle;

import java.util.Arrays;
import java.util.List;

public record TattooStyleDto(
        Long id,
        String slug,
        String name,
        String imageUrl,
        List<String> imageUrls
) {
    public static TattooStyleDto from(TattooStyle s) {
        return new TattooStyleDto(
                s.getId(),
                s.getSlug(),
                s.getName(),
                s.getImageUrl(),
                s.getImageUrls() != null ? Arrays.asList(s.getImageUrls()) : List.of()
        );
    }
}
