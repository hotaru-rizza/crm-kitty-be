package com.inkflow.crm.module.catalog.mapper;

import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStyle;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TattooMapper {

    public TattooDto toDto(Tattoo tattoo) {
        return new TattooDto(
                tattoo.getId(),
                tattoo.getStaffId(),
                tattoo.getStatus().name(),
                tattoo.getImageUrl(),
                tattoo.getThumbnailUrl(),
                tattoo.getWidth(),
                tattoo.getHeight(),
                tattoo.getBlurHash(),
                tattoo.getDominantColor(),
                tattoo.getAuthorName(),
                tattoo.getAuthorUrl(),
                tattoo.getDescription(),
                tattoo.getAltDescription(),
                tattoo.getTags() != null ? Arrays.asList(tattoo.getTags()) : List.of(),
                tattoo.isShowcase()
        );
    }

    public List<TattooDto> toDtoList(List<Tattoo> tattoos) {
        return tattoos.stream().map(this::toDto).toList();
    }

    public TattooStyleDto toStyleDto(TattooStyle style) {
        return toStyleDto(style, null);
    }

    public TattooStyleDto toStyleDto(TattooStyle style, String catalogCoverUrl) {
        String imageUrl = catalogCoverUrl != null && !catalogCoverUrl.isBlank()
                ? catalogCoverUrl
                : style.getImageUrl();

        return new TattooStyleDto(
                style.getId(),
                style.getSlug(),
                style.getName(),
                imageUrl,
                style.getImageUrls() != null ? Arrays.asList(style.getImageUrls()) : List.of()
        );
    }

    public List<TattooStyleDto> toStyleDtoList(List<TattooStyle> styles) {
        return styles.stream().map(this::toStyleDto).toList();
    }
}
