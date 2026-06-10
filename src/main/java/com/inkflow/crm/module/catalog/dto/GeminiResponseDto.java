package com.inkflow.crm.module.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponseDto(List<Candidate> candidates, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text, @JsonProperty("inline_data") InlineData inlineData) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InlineData(@JsonProperty("mime_type") String mimeType, String data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String message) {}
}
