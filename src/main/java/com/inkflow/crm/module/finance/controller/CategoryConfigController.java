package com.inkflow.crm.module.finance.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.finance.dto.CategoryConfigDto;
import com.inkflow.crm.module.finance.dto.CategoryConfigUpsertRequest;
import com.inkflow.crm.module.finance.service.CategoryConfigService;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/finance/categories")
@RequiredArgsConstructor
@Tag(name = "CRM · Finance")
public class CategoryConfigController {

    private final CategoryConfigService service;

    @GetMapping
    @RequirePermission(Permission.FINANCE_VIEW)
    public ResponseEntity<ApiResponse<List<CategoryConfigDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(service.getAll()));
    }

    @PutMapping("/{key}")
    @RequirePermission(Permission.FINANCE_CREATE)
    public ResponseEntity<ApiResponse<CategoryConfigDto>> upsert(
            @PathVariable String key,
            @RequestBody CategoryConfigUpsertRequest req) {
        CategoryConfigDto config = service.upsert(key, req.getLabel(), req.getColor(), req.getPlType());
        log.info("Category config upserted via API: key={}", key);

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PostMapping
    @RequirePermission(Permission.FINANCE_CREATE)
    public ResponseEntity<ApiResponse<CategoryConfigDto>> create(@RequestBody CategoryConfigUpsertRequest req) {
        CategoryConfigDto config = service.create(req.getLabel(), req.getColor(), req.getPlType());
        log.info("Category config created via API: label={}", req.getLabel());

        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.FINANCE_CREATE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        log.info("Category config deleted via API: id={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
