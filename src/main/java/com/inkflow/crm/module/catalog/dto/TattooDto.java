package com.inkflow.crm.module.catalog.dto;

import com.inkflow.crm.module.catalog.entity.Tattoo;

import java.util.Arrays;
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
    public static TattooDto from(Tattoo t) {
        return new TattooDto(
                t.getId(),
                t.getStaffId(),
                t.getStatus().name(),
                t.getImageUrl(),
                t.getThumbnailUrl(),
                t.getWidth(),
                t.getHeight(),
                t.getBlurHash(),
                t.getDominantColor(),
                t.getAuthorName(),
                t.getAuthorUrl(),
                t.getDescription(),
                t.getAltDescription(),
                t.getTags() != null ? Arrays.asList(t.getTags()) : List.of(),
                t.isShowcase()
        );
    }
}
