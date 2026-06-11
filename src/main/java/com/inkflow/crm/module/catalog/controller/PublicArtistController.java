package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.module.catalog.dto.PublicArtistDto;
import com.inkflow.crm.module.catalog.service.PublicArtistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/public/artists")
@RequiredArgsConstructor
public class PublicArtistController {

    private final PublicArtistService publicArtistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicArtistDto>>> getAll(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String q) {
        return ApiResponses.ok(publicArtistService.findAll(city, style, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicArtistDto>> getById(@PathVariable UUID id) {
        PublicArtistDto artist = publicArtistService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND, "Artist not found: " + id));
        return ApiResponses.ok(artist);
    }
}
