package com.inkflow.crm.module.consumer.dto;

public record GenerateRequest(
        String prompt,
        String style,
        String colorMode,
        String background,
        String ratio,
        String bodyImage
) {
}
