package com.inkflow.crm.module.consumer.mapper;

import com.inkflow.crm.module.consumer.dto.ConsumerUserDto;
import com.inkflow.crm.module.consumer.entity.AiGeneration;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ConsumerUserMapper {

    public ConsumerUserDto toDto(ConsumerUser user, List<AiGeneration> generations) {
        return new ConsumerUserDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getAiTokens(),
                user.getSavedTattooIds(),
                user.getSavedArtistIds(),
                user.getFavoriteGenerationIds().stream().map(UUID::toString).toList(),
                generations.stream().map(this::toGenerationDto).toList()
        );
    }

    private ConsumerUserDto.AiGenerationDto toGenerationDto(AiGeneration generation) {
        return new ConsumerUserDto.AiGenerationDto(
                generation.getId(),
                generation.getImageUrl(),
                generation.getPrompt(),
                generation.getCreatedAt()
        );
    }
}
