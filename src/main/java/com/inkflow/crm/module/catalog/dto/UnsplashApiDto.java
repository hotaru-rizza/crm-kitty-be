package com.inkflow.crm.module.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class UnsplashApiDto {

    private UnsplashApiDto() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(List<Photo> results, int total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Photo(
            String id,
            int width,
            int height,
            @JsonProperty("blur_hash") String blurHash,
            String color,
            String description,
            @JsonProperty("alt_description") String altDescription,
            Urls urls,
            User user,
            List<Tag> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Urls(String raw, String full, String regular, String small, String thumb) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String name, UserLinks links) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserLinks(String html) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tag(String title) {}
}
