package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.module.consumer.dto.ConsumerUserDto;
import com.inkflow.crm.module.consumer.dto.SaveGenerationRequest;
import com.inkflow.crm.module.consumer.dto.UpdateConsumerProfileRequest;
import com.inkflow.crm.module.consumer.entity.AiGeneration;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.mapper.ConsumerUserMapper;
import com.inkflow.crm.module.consumer.repository.AiGenerationRepository;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.module.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerUserServiceTest {

    @Mock
    private ConsumerUserRepository userRepository;

    @Mock
    private AiGenerationRepository generationRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ConsumerUserMapper consumerUserMapper;

    @InjectMocks
    private ConsumerUserService consumerUserService;

    @Test
    void getProfile_requiresAuthenticatedUser() {
        assertThrows(ApiException.class, () -> consumerUserService.getProfile(null));
    }

    @Test
    void updateProfile_updatesName() {
        ConsumerUser user = user(UUID.randomUUID());
        ConsumerUserDto dto = new ConsumerUserDto(
                user.getId(), user.getEmail(), "New Name", null, 5,
                List.of(), List.of(), List.of(), List.of()
        );

        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(dto);

        ConsumerUserDto result = consumerUserService.updateProfile(user, new UpdateConsumerProfileRequest("New Name", null));

        assertEquals("New Name", user.getName());
        assertEquals("New Name", result.name());
        verify(userRepository).save(user);
    }

    @Test
    void saveTattoo_addsIdOnce() {
        ConsumerUser user = user(UUID.randomUUID());
        user.setSavedTattooIds(new ArrayList<>());
        ConsumerUserDto dto = new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(42L), List.of(), List.of(), List.of()
        );

        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(dto);

        consumerUserService.saveTattoo(user, 42L);
        consumerUserService.saveTattoo(user, 42L);

        assertEquals(1, user.getSavedTattooIds().size());
        assertEquals(42L, user.getSavedTattooIds().getFirst());
    }

    @Test
    void unsaveTattoo_removesId() {
        ConsumerUser user = user(UUID.randomUUID());
        user.setSavedTattooIds(new ArrayList<>(List.of(42L)));

        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(), List.of(), List.of()
        ));

        consumerUserService.unsaveTattoo(user, 42L);

        assertTrue(user.getSavedTattooIds().isEmpty());
    }

    @Test
    void saveGeneration_uploadsImageAndPersists() {
        ConsumerUser user = user(UUID.randomUUID());
        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        when(fileStorageService.uploadBytes(any(), anyString(), eq("image/png")))
                .thenReturn("https://cdn.example.com/generations/saved.png");
        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(), List.of(), List.of()
        ));

        consumerUserService.saveGeneration(user, new SaveGenerationRequest(dataUri, "dragon on arm"));

        verify(fileStorageService).uploadBytes(any(), anyString(), eq("image/png"));
        verify(generationRepository).save(any(AiGeneration.class));
    }

    @Test
    void favoriteGeneration_addsIdOnce() {
        ConsumerUser user = user(UUID.randomUUID());
        UUID generationId = UUID.randomUUID();

        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(), List.of(generationId.toString()), List.of()
        ));

        consumerUserService.favoriteGeneration(user, generationId);
        consumerUserService.favoriteGeneration(user, generationId);

        assertEquals(1, user.getFavoriteGenerationIds().size());
        assertEquals(generationId, user.getFavoriteGenerationIds().getFirst());
    }

    @Test
    void saveArtist_addsIdOnce() {
        ConsumerUser user = user(UUID.randomUUID());
        String artistId = UUID.randomUUID().toString();

        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(artistId), List.of(), List.of()
        ));

        consumerUserService.saveArtist(user, artistId);
        consumerUserService.saveArtist(user, artistId);

        assertEquals(1, user.getSavedArtistIds().size());
        assertEquals(artistId, user.getSavedArtistIds().getFirst());
    }

    @Test
    void updateProfile_updatesOnlyAvatarWhenNameNull() {
        ConsumerUser user = user(UUID.randomUUID());
        user.setName("Alex");

        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), "Alex", "https://cdn.example.com/avatar.png", 5,
                List.of(), List.of(), List.of(), List.of()
        ));

        consumerUserService.updateProfile(user, new UpdateConsumerProfileRequest(null, "https://cdn.example.com/avatar.png"));

        assertEquals("Alex", user.getName());
        assertEquals("https://cdn.example.com/avatar.png", user.getAvatarUrl());
    }

    @Test
    void saveGeneration_persistsPromptAndImageUrl() {
        ConsumerUser user = user(UUID.randomUUID());
        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        when(fileStorageService.uploadBytes(any(), anyString(), eq("image/png")))
                .thenReturn("https://cdn.example.com/generations/saved.png");
        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(), List.of(), List.of()
        ));

        consumerUserService.saveGeneration(user, new SaveGenerationRequest(dataUri, "dragon on arm"));

        ArgumentCaptor<AiGeneration> captor = ArgumentCaptor.forClass(AiGeneration.class);
        verify(generationRepository).save(captor.capture());

        AiGeneration saved = captor.getValue();
        assertEquals("dragon on arm", saved.getPrompt());
        assertEquals("https://cdn.example.com/generations/saved.png", saved.getImageUrl());
        assertEquals(user, saved.getUser());
    }

    @Test
    void deleteGeneration_removesOwnedGeneration() {
        ConsumerUser user = user(UUID.randomUUID());
        UUID generationId = UUID.randomUUID();
        AiGeneration generation = new AiGeneration(user, "https://cdn.example.com/gen.png", "dragon");

        when(generationRepository.findById(generationId)).thenReturn(Optional.of(generation));
        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(), List.of(), List.of()
        ));

        consumerUserService.deleteGeneration(user, generationId);

        verify(generationRepository).delete(generation);
    }

    @Test
    void deleteGeneration_skipsWhenNotOwnedByUser() {
        ConsumerUser user = user(UUID.randomUUID());
        ConsumerUser otherUser = user(UUID.randomUUID());
        UUID generationId = UUID.randomUUID();
        AiGeneration generation = new AiGeneration(otherUser, "https://cdn.example.com/gen.png", "rose");

        when(generationRepository.findById(generationId)).thenReturn(Optional.of(generation));
        when(generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());
        when(userRepository.save(user)).thenReturn(user);
        when(consumerUserMapper.toDto(eq(user), any())).thenReturn(new ConsumerUserDto(
                user.getId(), user.getEmail(), user.getName(), null, 5,
                List.of(), List.of(), List.of(), List.of()
        ));

        consumerUserService.deleteGeneration(user, generationId);

        verify(generationRepository, never()).delete(any(AiGeneration.class));
        verify(userRepository).save(user);
    }

    private ConsumerUser user(UUID id) {
        ConsumerUser user = new ConsumerUser(id, "user@test.com", "Alex");
        user.setSavedTattooIds(new ArrayList<>());
        user.setSavedArtistIds(new ArrayList<>());
        user.setFavoriteGenerationIds(new ArrayList<>());
        return user;
    }
}
