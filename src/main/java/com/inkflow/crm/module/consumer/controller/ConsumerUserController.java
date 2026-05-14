package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.module.consumer.dto.ConsumerUserDto;
import com.inkflow.crm.module.consumer.entity.AiGeneration;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.AiGenerationRepository;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.module.storage.service.FileStorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/public/consumer/users")
@RequiredArgsConstructor
@Transactional
public class ConsumerUserController {

    private final ConsumerUserRepository userRepository;
    private final AiGenerationRepository generationRepository;
    private final FileStorageService fileStorageService;

    public record UpdateProfileRequest(String name, String avatarUrl) {}
    public record SaveGenerationRequest(String imageDataUri, String prompt) {}

    @GetMapping("/me")
    public ResponseEntity<ConsumerUserDto> getMe(@AuthenticationPrincipal ConsumerUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(toDto(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<ConsumerUserDto> updateMe(
            @AuthenticationPrincipal ConsumerUser user,
            @RequestBody UpdateProfileRequest body) {
        if (user == null) return ResponseEntity.status(401).build();
        if (body.name() != null) user.setName(body.name());
        if (body.avatarUrl() != null) user.setAvatarUrl(body.avatarUrl());
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/me/saved-tattoos/{tattooId}")
    public ResponseEntity<ConsumerUserDto> saveTattoo(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable Long tattooId) {
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.getSavedTattooIds().contains(tattooId)) user.getSavedTattooIds().add(tattooId);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/me/saved-tattoos/{tattooId}")
    public ResponseEntity<ConsumerUserDto> unsaveTattoo(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable Long tattooId) {
        if (user == null) return ResponseEntity.status(401).build();
        user.getSavedTattooIds().remove(tattooId);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/me/saved-artists/{artistId}")
    public ResponseEntity<ConsumerUserDto> saveArtist(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable String artistId) {
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.getSavedArtistIds().contains(artistId)) user.getSavedArtistIds().add(artistId);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/me/saved-artists/{artistId}")
    public ResponseEntity<ConsumerUserDto> unsaveArtist(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable String artistId) {
        if (user == null) return ResponseEntity.status(401).build();
        user.getSavedArtistIds().remove(artistId);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/me/generations")
    public ResponseEntity<ConsumerUserDto> saveGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @RequestBody SaveGenerationRequest body) {
        if (user == null) return ResponseEntity.status(401).build();
        String imageUrl = uploadDataUri(body.imageDataUri());
        generationRepository.save(new AiGeneration(user, imageUrl, body.prompt()));
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/me/favorite-generations/{generationId}")
    public ResponseEntity<ConsumerUserDto> favoriteGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable UUID generationId) {
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.getFavoriteGenerationIds().contains(generationId)) {
            user.getFavoriteGenerationIds().add(generationId);
        }
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/me/favorite-generations/{generationId}")
    public ResponseEntity<ConsumerUserDto> unfavoriteGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable UUID generationId) {
        if (user == null) return ResponseEntity.status(401).build();
        user.getFavoriteGenerationIds().remove(generationId);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/me/generations/{generationId}")
    public ResponseEntity<ConsumerUserDto> deleteGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable UUID generationId) {
        if (user == null) return ResponseEntity.status(401).build();
        generationRepository.findById(generationId).ifPresent(g -> {
            if (g.getUser().getId().equals(user.getId())) generationRepository.delete(g);
        });
        user.getFavoriteGenerationIds().remove(generationId);
        userRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    private ConsumerUserDto toDto(ConsumerUser user) {
        return ConsumerUserDto.from(user, generationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
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
