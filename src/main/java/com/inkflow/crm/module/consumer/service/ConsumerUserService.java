package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.module.consumer.dto.ConsumerUserDto;
import com.inkflow.crm.module.consumer.dto.SaveGenerationRequest;
import com.inkflow.crm.module.consumer.dto.UpdateConsumerProfileRequest;
import com.inkflow.crm.module.consumer.entity.AiGeneration;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.mapper.ConsumerUserMapper;
import com.inkflow.crm.module.consumer.repository.AiGenerationRepository;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.module.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumerUserService {

    private final ConsumerUserRepository userRepository;
    private final AiGenerationRepository generationRepository;
    private final FileStorageService fileStorageService;
    private final ConsumerUserMapper consumerUserMapper;

    @Transactional(readOnly = true)
    public ConsumerUserDto getProfile(ConsumerUser user) {
        return toDto(requireUser(user));
    }

    @Transactional
    public ConsumerUserDto updateProfile(ConsumerUser user, UpdateConsumerProfileRequest body) {
        return mutate(user, current -> {
            if (body.name() != null) {
                current.setName(body.name());
            }
            if (body.avatarUrl() != null) {
                current.setAvatarUrl(body.avatarUrl());
            }
        }, "Consumer profile updated: userId={}");
    }

    @Transactional
    public ConsumerUserDto saveTattoo(ConsumerUser user, Long tattooId) {
        return mutate(user, current -> addIfAbsent(current.getSavedTattooIds(), tattooId),
                "Consumer tattoo saved: userId={} tattooId={}", tattooId);
    }

    @Transactional
    public ConsumerUserDto unsaveTattoo(ConsumerUser user, Long tattooId) {
        return mutate(user, current -> current.getSavedTattooIds().remove(tattooId),
                "Consumer tattoo unsaved: userId={} tattooId={}", tattooId);
    }

    @Transactional
    public ConsumerUserDto saveArtist(ConsumerUser user, String artistId) {
        return mutate(user, current -> addIfAbsent(current.getSavedArtistIds(), artistId),
                "Consumer artist saved: userId={} artistId={}", artistId);
    }

    @Transactional
    public ConsumerUserDto unsaveArtist(ConsumerUser user, String artistId) {
        return mutate(user, current -> current.getSavedArtistIds().remove(artistId),
                "Consumer artist unsaved: userId={} artistId={}", artistId);
    }

    @Transactional
    public ConsumerUserDto saveGeneration(ConsumerUser user, SaveGenerationRequest body) {
        ConsumerUser current = requireUser(user);
        String imageUrl = uploadDataUri(body.imageDataUri());
        generationRepository.save(new AiGeneration(current, imageUrl, body.prompt()));

        log.info("AI generation saved: userId={}", current.getId());
        return toDto(current);
    }

    @Transactional
    public ConsumerUserDto favoriteGeneration(ConsumerUser user, UUID generationId) {
        return mutate(user, current -> addIfAbsent(current.getFavoriteGenerationIds(), generationId),
                "AI generation favorited: userId={} generationId={}", generationId);
    }

    @Transactional
    public ConsumerUserDto unfavoriteGeneration(ConsumerUser user, UUID generationId) {
        return mutate(user, current -> current.getFavoriteGenerationIds().remove(generationId),
                "AI generation unfavorited: userId={} generationId={}", generationId);
    }

    @Transactional
    public ConsumerUserDto deleteGeneration(ConsumerUser user, UUID generationId) {
        ConsumerUser current = requireUser(user);

        generationRepository.findById(generationId).ifPresent(generation -> {
            if (generation.getUser().getId().equals(current.getId())) {
                generationRepository.delete(generation);
            }
        });

        current.getFavoriteGenerationIds().remove(generationId);
        userRepository.save(current);

        log.info("AI generation deleted: userId={} generationId={}", current.getId(), generationId);
        return toDto(current);
    }

    private ConsumerUserDto mutate(ConsumerUser user, Consumer<ConsumerUser> change, String logPattern, Object... extraArgs) {
        ConsumerUser current = requireUser(user);
        change.accept(current);
        userRepository.save(current);

        Object[] logArgs = new Object[extraArgs.length + 1];
        logArgs[0] = current.getId();
        System.arraycopy(extraArgs, 0, logArgs, 1, extraArgs.length);
        log.info(logPattern, logArgs);

        return toDto(current);
    }

    private <T> void addIfAbsent(Collection<T> collection, T value) {
        if (!collection.contains(value)) {
            collection.add(value);
        }
    }

    private ConsumerUser requireUser(ConsumerUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private ConsumerUserDto toDto(ConsumerUser user) {
        return consumerUserMapper.toDto(
                user,
                generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
        );
    }

    private String uploadDataUri(String dataUri) {
        String[] parts = dataUri.split(",", 2);
        String meta = parts[0];
        byte[] data = Base64.getDecoder().decode(parts[1]);
        String ext = meta.contains("png") ? ".png" : meta.contains("webp") ? ".webp" : ".jpg";
        String contentType = meta.contains("png") ? "image/png" : meta.contains("webp") ? "image/webp" : "image/jpeg";
        String key = "generations/" + UUID.randomUUID() + ext;
        return fileStorageService.uploadBytes(data, key, contentType);
    }
}
