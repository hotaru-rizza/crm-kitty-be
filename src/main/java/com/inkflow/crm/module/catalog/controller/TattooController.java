package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.service.EmbeddingService;
import com.inkflow.crm.module.catalog.service.TattooTaggerService;
import com.inkflow.crm.module.catalog.service.UnsplashSeederService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public/catalog/tattoos")
@RequiredArgsConstructor
public class TattooController {

    private final TattooRepository tattooRepository;
    private final UnsplashSeederService seederService;
    private final TattooTaggerService taggerService;
    private final EmbeddingService embeddingService;

    @GetMapping
    public Page<TattooDto> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String author
    ) {
        return tattooRepository.findByTagOrAll(tag, author, PageRequest.of(page, size))
                .map(TattooDto::from);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TattooDto> getById(@PathVariable Long id) {
        return tattooRepository.findById(id)
                .map(TattooDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<TattooDto> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        float[] embedding = embeddingService.embed(q);
        String pgVector = toVectorString(embedding);
        return tattooRepository.findByEmbedding(pgVector, limit)
                .stream().map(TattooDto::from).toList();
    }

    @GetMapping("/{id}/similar")
    public List<TattooDto> getSimilar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "12") int limit
    ) {
        return tattooRepository.findSimilar(id, limit)
                .stream()
                .map(TattooDto::from)
                .toList();
    }

    @GetMapping("/by-ids")
    public List<TattooDto> getByIds(@RequestParam List<Long> ids) {
        return tattooRepository.findAllById(ids).stream()
                .map(TattooDto::from)
                .toList();
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed() {
        int count = seederService.seed();
        return ResponseEntity.ok(Map.of("saved", count, "total", tattooRepository.count()));
    }

    @PostMapping("/retag")
    public ResponseEntity<Map<String, Object>> retag() {
        int count = taggerService.retagAll();
        return ResponseEntity.ok(Map.of("retagged", count));
    }

    private String toVectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }
}
