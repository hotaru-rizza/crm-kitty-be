package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.consumer.dto.UpdateConsumerProfileRequest;
import com.inkflow.crm.module.consumer.entity.AiGeneration;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.AiGenerationRepository;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.module.consumer.dto.SaveGenerationRequest;
import com.inkflow.crm.module.storage.service.FileStorageService;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.consumerUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ConsumerUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConsumerUserRepository consumerUserRepository;

    @Autowired
    private AiGenerationRepository aiGenerationRepository;

    @MockBean
    private FileStorageService fileStorageService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getMe_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/public/consumer/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getMe_withConsumerAuth_returnsProfile() throws Exception {
        ConsumerUser user = seedConsumer("Alex");

        mockMvc.perform(get("/public/consumer/users/me")
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));
    }

    @Test
    void updateMe_withConsumerAuth_updatesProfile() throws Exception {
        ConsumerUser user = seedConsumer(null);
        UpdateConsumerProfileRequest body = new UpdateConsumerProfileRequest("New Name", "https://cdn.example.com/avatar.png");

        mockMvc.perform(patch("/public/consumer/users/me")
                        .with(consumerUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example.com/avatar.png"));

        ConsumerUser persisted = consumerUserRepository.findById(user.getId()).orElseThrow();
        assertEquals("New Name", persisted.getName());
        assertEquals("https://cdn.example.com/avatar.png", persisted.getAvatarUrl());
    }

    @Test
    void saveTattoo_withConsumerAuth_addsTattooId() throws Exception {
        ConsumerUser user = seedConsumer("Alex");

        mockMvc.perform(post("/public/consumer/users/me/saved-tattoos/{tattooId}", 42L)
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedTattooIds[0]").value(42));

        ConsumerUser persisted = consumerUserRepository.findById(user.getId()).orElseThrow();
        assertEquals(1, persisted.getSavedTattooIds().size());
        assertEquals(42L, persisted.getSavedTattooIds().getFirst());
    }

    @Test
    void unsaveTattoo_withConsumerAuth_removesTattooId() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        user.getSavedTattooIds().add(42L);
        consumerUserRepository.save(user);

        mockMvc.perform(delete("/public/consumer/users/me/saved-tattoos/{tattooId}", 42L)
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedTattooIds").isEmpty());
    }

    @Test
    void saveArtist_withConsumerAuth_addsArtistId() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        String artistId = UUID.randomUUID().toString();

        mockMvc.perform(post("/public/consumer/users/me/saved-artists/{artistId}", artistId)
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedArtistIds[0]").value(artistId));
    }

    @Test
    void unsaveArtist_withConsumerAuth_removesArtistId() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        String artistId = UUID.randomUUID().toString();
        user.getSavedArtistIds().add(artistId);
        consumerUserRepository.save(user);

        mockMvc.perform(delete("/public/consumer/users/me/saved-artists/{artistId}", artistId)
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedArtistIds").isEmpty());
    }

    @Test
    void favoriteGeneration_withConsumerAuth_addsFavorite() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        AiGeneration generation = aiGenerationRepository.save(new AiGeneration(user, "https://cdn.example.com/gen.png", "rose"));

        mockMvc.perform(post("/public/consumer/users/me/favorite-generations/{generationId}", generation.getId())
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteGenerationIds[0]").value(generation.getId().toString()));
    }

    @Test
    void unfavoriteGeneration_withConsumerAuth_removesFavorite() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        AiGeneration generation = aiGenerationRepository.save(new AiGeneration(user, "https://cdn.example.com/gen.png", "rose"));
        user.getFavoriteGenerationIds().add(generation.getId());
        consumerUserRepository.save(user);

        mockMvc.perform(delete("/public/consumer/users/me/favorite-generations/{generationId}", generation.getId())
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteGenerationIds").isEmpty());
    }

    @Test
    void deleteGeneration_withConsumerAuth_removesGeneration() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        AiGeneration generation = aiGenerationRepository.save(new AiGeneration(user, "https://cdn.example.com/gen.png", "rose"));
        user.getFavoriteGenerationIds().add(generation.getId());
        consumerUserRepository.save(user);

        mockMvc.perform(delete("/public/consumer/users/me/generations/{generationId}", generation.getId())
                        .with(consumerUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiGenerations").isEmpty())
                .andExpect(jsonPath("$.data.favoriteGenerationIds").isEmpty());

        assertTrue(aiGenerationRepository.findById(generation.getId()).isEmpty());
        ConsumerUser persisted = consumerUserRepository.findById(user.getId()).orElseThrow();
        assertTrue(persisted.getFavoriteGenerationIds().isEmpty());
    }

    @Test
    void saveGeneration_withConsumerAuth_persistsGeneration() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        SaveGenerationRequest body = new SaveGenerationRequest(dataUri, "dragon on arm");

        when(fileStorageService.uploadBytes(any(), anyString(), eq("image/png")))
                .thenReturn("https://cdn.example.com/generations/saved.png");

        mockMvc.perform(post("/public/consumer/users/me/generations")
                        .with(consumerUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiGenerations[0].prompt").value("dragon on arm"))
                .andExpect(jsonPath("$.data.aiGenerations[0].imageUrl").value("https://cdn.example.com/generations/saved.png"));

        assertEquals(1, aiGenerationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).size());
        AiGeneration persisted = aiGenerationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).getFirst();
        assertEquals("dragon on arm", persisted.getPrompt());
        assertEquals("https://cdn.example.com/generations/saved.png", persisted.getImageUrl());
        assertEquals(user.getId(), persisted.getUser().getId());
    }

    @Test
    void saveGeneration_withBlankPrompt_returnsBadRequest() throws Exception {
        ConsumerUser user = seedConsumer("Alex");
        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        SaveGenerationRequest body = new SaveGenerationRequest(dataUri, "");

        mockMvc.perform(post("/public/consumer/users/me/generations")
                        .with(consumerUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    private ConsumerUser seedConsumer(String name) {
        UUID id = UUID.randomUUID();
        return consumerUserRepository.save(new ConsumerUser(id, "consumer-" + id + "@test.com", name));
    }
}
