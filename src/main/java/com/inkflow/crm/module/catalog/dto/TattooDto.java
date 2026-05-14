package com.inkflow.crm.module.catalog.dto;

import com.inkflow.crm.module.catalog.entity.Tattoo;

import java.util.Arrays;
import java.util.List;

public record TattooDto(
        Long id,
        String imageUrl,
        String thumbnailUrl,
        int width,
        int height,
        String blurHash,
        String dominantColor,
        String authorName,
        String authorUrl,
        String description,
        List<String> tags
) {
    public static TattooDto from(Tattoo t) {
        return new TattooDto(
                t.getId(),
                t.getImageUrl(),
                t.getThumbnailUrl(),
                t.getWidth(),
                t.getHeight(),
                t.getBlurHash(),
                t.getDominantColor(),
                t.getAuthorName(),
                t.getAuthorUrl(),
                t.getDescription() != null ? t.getDescription() : t.getAltDescription(),
                t.getTags() != null ? Arrays.asList(t.getTags()) : List.of()
        );
    }
}
