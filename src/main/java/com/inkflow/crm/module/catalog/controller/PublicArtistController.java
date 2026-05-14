package com.inkflow.crm.module.catalog.controller;

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
    public List<PublicArtistDto> getAll() {
        return publicArtistService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicArtistDto> getById(@PathVariable UUID id) {
        return publicArtistService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
