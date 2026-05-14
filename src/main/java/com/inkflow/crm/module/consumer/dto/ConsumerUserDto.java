package com.inkflow.crm.module.consumer.dto;

import com.inkflow.crm.module.consumer.entity.AiGeneration;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;

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
        public static AiGenerationDto from(AiGeneration g) {
            return new AiGenerationDto(g.getId(), g.getImageUrl(), g.getPrompt(), g.getCreatedAt());
        }
    }

    public static ConsumerUserDto from(ConsumerUser user, List<AiGeneration> generations) {
        return new ConsumerUserDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getAiTokens(),
                user.getSavedTattooIds(),
                user.getSavedArtistIds(),
                user.getFavoriteGenerationIds().stream().map(UUID::toString).toList(),
                generations.stream().map(AiGenerationDto::from).toList()
        );
    }
}
