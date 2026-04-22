package com.inkflow.crm.module.promotion;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.promotion.dto.CreatePromotionRequest;
import com.inkflow.crm.module.promotion.dto.PromotionDto;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    @RequirePermission("calendar.view_all")
    public ResponseEntity<ApiResponse<List<PromotionDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(promotionService.getAllPromotions()));
    }

    @GetMapping("/active")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<PromotionDto>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(promotionService.getActivePromotions()));
    }

    @PostMapping
    @RequirePermission("calendar.view_all")
    public ResponseEntity<ApiResponse<PromotionDto>> create(@Valid @RequestBody CreatePromotionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(promotionService.createPromotion(req)));
    }

    @PatchMapping("/{id}")
    @RequirePermission("calendar.view_all")
    public ResponseEntity<ApiResponse<PromotionDto>> update(@PathVariable UUID id, @RequestBody CreatePromotionRequest req) {
        return ResponseEntity.ok(ApiResponse.success(promotionService.updatePromotion(id, req)));
    }

    @PatchMapping("/{id}/toggle")
    @RequirePermission("calendar.view_all")
    public ResponseEntity<ApiResponse<Void>> toggle(@PathVariable UUID id) {
        promotionService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @DeleteMapping("/{id}")
    @RequirePermission("calendar.view_all")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
