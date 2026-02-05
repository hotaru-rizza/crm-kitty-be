package com.inkflow.crm.module.booking.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.booking.dto.*;
import com.inkflow.crm.module.booking.service.PublicBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public API for client-facing booking interface.
 * No authentication required - accessed by subdomain.
 */
@RestController
@RequestMapping("/public/book")
@RequiredArgsConstructor
public class PublicBookingController {

    private final PublicBookingService bookingService;

    /**
     * Get salon info by subdomain
     * GET /api/public/book/{subdomain}
     */
    @GetMapping("/{subdomain}")
    public ResponseEntity<ApiResponse<PublicSalonDto>> getSalon(@PathVariable String subdomain) {
        PublicSalonDto salon = bookingService.getSalonBySubdomain(subdomain);
        return ResponseEntity.ok(ApiResponse.success(salon));
    }

    /**
     * Get all artists for a salon
     * GET /api/public/book/{subdomain}/artists
     */
    @GetMapping("/{subdomain}/artists")
    public ResponseEntity<ApiResponse<List<PublicArtistDto>>> getArtists(@PathVariable String subdomain) {
        List<PublicArtistDto> artists = bookingService.getArtistsBySubdomain(subdomain);
        return ResponseEntity.ok(ApiResponse.success(artists));
    }

    /**
     * Get artist details
     * GET /api/public/book/{subdomain}/artists/{artistId}
     */
    @GetMapping("/{subdomain}/artists/{artistId}")
    public ResponseEntity<ApiResponse<PublicArtistDto>> getArtist(
            @PathVariable String subdomain,
            @PathVariable UUID artistId) {
        PublicArtistDto artist = bookingService.getArtistById(subdomain, artistId);
        return ResponseEntity.ok(ApiResponse.success(artist));
    }

    /**
     * Get all services for a salon
     * GET /api/public/book/{subdomain}/services
     */
    @GetMapping("/{subdomain}/services")
    public ResponseEntity<ApiResponse<List<PublicServiceDto>>> getServices(@PathVariable String subdomain) {
        List<PublicServiceDto> services = bookingService.getServicesBySubdomain(subdomain);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    /**
     * Get services for a specific artist (with artist-specific pricing)
     * GET /api/public/book/{subdomain}/artists/{artistId}/services
     */
    @GetMapping("/{subdomain}/artists/{artistId}/services")
    public ResponseEntity<ApiResponse<List<PublicServiceDto>>> getArtistServices(
            @PathVariable String subdomain,
            @PathVariable UUID artistId) {
        List<PublicServiceDto> services = bookingService.getArtistServices(subdomain, artistId);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    /**
     * Get available time slots for an artist and service
     * GET /api/public/book/{subdomain}/slots?artistId=...&serviceId=...&from=...&to=...
     */
    @GetMapping("/{subdomain}/slots")
    public ResponseEntity<ApiResponse<List<TimeSlotDto>>> getAvailableSlots(
            @PathVariable String subdomain,
            @RequestParam UUID artistId,
            @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<TimeSlotDto> slots = bookingService.getAvailableSlots(subdomain, artistId, serviceId, from, to);
        return ResponseEntity.ok(ApiResponse.success(slots));
    }

    /**
     * Create a booking request
     * POST /api/public/book/{subdomain}
     */
    @PostMapping("/{subdomain}")
    public ResponseEntity<ApiResponse<BookingConfirmationDto>> createBooking(
            @PathVariable String subdomain,
            @Valid @RequestBody CreateBookingRequest request) {
        UUID requestId = bookingService.createBooking(subdomain, request);
        
        BookingConfirmationDto confirmation = BookingConfirmationDto.builder()
                .requestId(requestId)
                .message("Ваш запис успішно створено! Ми зв'яжемося з вами для підтвердження.")
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(confirmation));
    }
}
