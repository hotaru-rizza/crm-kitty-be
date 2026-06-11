package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.dto.ConsumerUserDto;
import com.inkflow.crm.module.consumer.dto.SaveGenerationRequest;
import com.inkflow.crm.module.consumer.dto.UpdateConsumerProfileRequest;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.service.ConsumerUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/public/consumer/users")
@RequiredArgsConstructor
public class ConsumerUserController {

    private final ConsumerUserService consumerUserService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> getMe(@AuthenticationPrincipal ConsumerUser user) {
        return ApiResponses.ok(consumerUserService.getProfile(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> updateMe(
            @AuthenticationPrincipal ConsumerUser user,
            @RequestBody UpdateConsumerProfileRequest body) {
        ConsumerUserDto updated = consumerUserService.updateProfile(user, body);
        log.info("Consumer profile updated via API: userId={}", user.getId());

        return ApiResponses.ok(updated);
    }

    @PostMapping("/me/saved-tattoos/{tattooId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> saveTattoo(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable Long tattooId) {
        ConsumerUserDto updated = consumerUserService.saveTattoo(user, tattooId);
        log.info("Consumer saved tattoo via API: userId={} tattooId={}", user.getId(), tattooId);

        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/me/saved-tattoos/{tattooId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> unsaveTattoo(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable Long tattooId) {
        ConsumerUserDto updated = consumerUserService.unsaveTattoo(user, tattooId);
        log.info("Consumer unsaved tattoo via API: userId={} tattooId={}", user.getId(), tattooId);

        return ApiResponses.ok(updated);
    }

    @PostMapping("/me/saved-artists/{artistId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> saveArtist(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable String artistId) {
        ConsumerUserDto updated = consumerUserService.saveArtist(user, artistId);
        log.info("Consumer saved artist via API: userId={} artistId={}", user.getId(), artistId);

        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/me/saved-artists/{artistId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> unsaveArtist(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable String artistId) {
        ConsumerUserDto updated = consumerUserService.unsaveArtist(user, artistId);
        log.info("Consumer unsaved artist via API: userId={} artistId={}", user.getId(), artistId);

        return ApiResponses.ok(updated);
    }

    @PostMapping("/me/generations")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> saveGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @RequestBody SaveGenerationRequest body) {
        ConsumerUserDto updated = consumerUserService.saveGeneration(user, body);
        log.info("Consumer saved generation via API: userId={}", user.getId());

        return ApiResponses.ok(updated);
    }

    @PostMapping("/me/favorite-generations/{generationId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> favoriteGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable UUID generationId) {
        ConsumerUserDto updated = consumerUserService.favoriteGeneration(user, generationId);
        log.info("Consumer favorited generation via API: userId={} generationId={}", user.getId(), generationId);

        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/me/favorite-generations/{generationId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> unfavoriteGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable UUID generationId) {
        ConsumerUserDto updated = consumerUserService.unfavoriteGeneration(user, generationId);
        log.info("Consumer unfavorited generation via API: userId={} generationId={}", user.getId(), generationId);

        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/me/generations/{generationId}")
    public ResponseEntity<ApiResponse<ConsumerUserDto>> deleteGeneration(
            @AuthenticationPrincipal ConsumerUser user,
            @PathVariable UUID generationId) {
        ConsumerUserDto updated = consumerUserService.deleteGeneration(user, generationId);
        log.info("Consumer deleted generation via API: userId={} generationId={}", user.getId(), generationId);

        return ApiResponses.ok(updated);
    }
}
