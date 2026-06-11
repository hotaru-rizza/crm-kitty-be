package com.inkflow.crm.module.consumer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsumerUserDto(
        UUID id,
        String email,
        String name,
        String avatarUrl,
        int aiTokens,
        List<Long> savedTattooIds,
        List<String> savedArtistIds,
        List<String> favoriteGenerationIds,
        List<AiGenerationDto> aiGenerations
) {
    public record AiGenerationDto(UUID id, String imageUrl, String prompt, Instant createdAt) {
    }
}
