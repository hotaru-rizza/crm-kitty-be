package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.catalog.dto.CatalogRetagResultDto;
import com.inkflow.crm.module.catalog.dto.CatalogSeedResultDto;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.service.TattooCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/public/catalog/tattoos")
@RequiredArgsConstructor
public class TattooController {

    private final TattooCatalogService tattooCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TattooDto>>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String staffId) {
        return ApiResponses.page(
                tattooCatalogService.getFeed(tag, author, staffId, PageRequest.of(page, size))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TattooDto>> getById(@PathVariable Long id) {
        return ApiResponses.ok(tattooCatalogService.getById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TattooDto>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.ok(tattooCatalogService.search(q, limit));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<ApiResponse<List<TattooDto>>> getSimilar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "12") int limit) {
        return ApiResponses.ok(tattooCatalogService.getSimilar(id, limit));
    }

    @GetMapping("/by-ids")
    public ResponseEntity<ApiResponse<List<TattooDto>>> getByIds(@RequestParam List<Long> ids) {
        return ApiResponses.ok(tattooCatalogService.getByIds(ids));
    }

    @GetMapping("/styles")
    public ResponseEntity<ApiResponse<List<TattooStyleDto>>> getStyles() {
        return ApiResponses.ok(tattooCatalogService.getStyles());
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<Set<String>>> getAvailableTags() {
        return ApiResponses.ok(tattooCatalogService.getAvailableTags());
    }

    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<CatalogSeedResultDto>> seed() {
        CatalogSeedResultDto result = tattooCatalogService.seed();
        log.info("Tattoo catalog seeded via API: saved={} total={}", result.saved(), result.total());

        return ApiResponses.ok(result);
    }

    @PostMapping("/retag")
    public ResponseEntity<ApiResponse<CatalogRetagResultDto>> retag() {
        CatalogRetagResultDto result = tattooCatalogService.retag();
        log.info("Tattoo catalog retagged via API: retagged={}", result.retagged());

        return ApiResponses.ok(result);
    }
}
