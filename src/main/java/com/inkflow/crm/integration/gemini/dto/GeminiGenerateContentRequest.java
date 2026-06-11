package com.inkflow.crm.integration.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiGenerateContentRequest(
        List<GeminiContent> contents,
        @JsonProperty("generationConfig") GeminiGenerationConfig generationConfig
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeminiContent(List<GeminiPart> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeminiPart(
            String text,
            @JsonProperty("inline_data") GeminiInlineData inlineData
    ) {
        public static GeminiPart text(String value) {
            return new GeminiPart(value, null);
        }

        public static GeminiPart jpegInline(String base64Data) {
            return new GeminiPart(null, new GeminiInlineData("image/jpeg", base64Data));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeminiInlineData(
            @JsonProperty("mime_type") String mimeType,
            String data
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeminiGenerationConfig(
            @JsonProperty("response_modalities") List<String> responseModalities,
            double temperature
    ) {}
}
