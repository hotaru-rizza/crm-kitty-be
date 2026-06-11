package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.catalog.dto.BulkUploadRequest;
import com.inkflow.crm.module.catalog.dto.SetShowcaseRequest;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.dto.UpdateTattooRequest;
import com.inkflow.crm.module.catalog.service.PortfolioService;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/staff/{staffId}/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    @RequirePermission("staff.view")
    public ResponseEntity<ApiResponse<List<TattooDto>>> getPortfolio(@PathVariable UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(portfolioService.getPortfolio(staffId)));
    }

    @PostMapping
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<List<TattooDto>>> upload(
            @PathVariable UUID staffId,
            @RequestBody BulkUploadRequest request) {
        List<TattooDto> created = portfolioService.uploadBulk(staffId, request.imageUrls());
        log.info("Portfolio bulk upload via API: staffId={} count={}", staffId, created.size());

        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PatchMapping("/{tattooId}")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<TattooDto>> update(
            @PathVariable UUID staffId,
            @PathVariable Long tattooId,
            @RequestBody UpdateTattooRequest request) {
        TattooDto updated = portfolioService.update(tattooId, request.description(), request.tags());
        log.info("Portfolio tattoo updated via API: staffId={} tattooId={}", staffId, tattooId);

        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PutMapping("/showcase")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<List<TattooDto>>> setShowcase(
            @PathVariable UUID staffId,
            @RequestBody SetShowcaseRequest request) {
        List<TattooDto> updated = portfolioService.setShowcase(staffId, request.tattooIds());
        log.info("Portfolio showcase updated via API: staffId={} count={}", staffId, request.tattooIds().size());

        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{tattooId}")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID staffId,
            @PathVariable Long tattooId) {
        portfolioService.delete(tattooId);
        log.info("Portfolio tattoo deleted via API: staffId={} tattooId={}", staffId, tattooId);

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
