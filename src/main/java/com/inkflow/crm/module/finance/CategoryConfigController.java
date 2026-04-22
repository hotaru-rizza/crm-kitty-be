package com.inkflow.crm.module.finance;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.finance.dto.CategoryConfigDto;
import com.inkflow.crm.security.RequirePermission;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/finance/categories")
@RequiredArgsConstructor
public class CategoryConfigController {

    private final CategoryConfigService service;

    @GetMapping
    @RequirePermission("finance.view")
    public ResponseEntity<ApiResponse<List<CategoryConfigDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(service.getAll()));
    }

    @PutMapping("/{key}")
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<CategoryConfigDto>> upsert(
            @PathVariable String key,
            @RequestBody UpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                service.upsert(key, req.getLabel(), req.getColor(), req.getPlType())
        ));
    }

    @PostMapping
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<CategoryConfigDto>> create(@RequestBody UpsertRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                service.create(req.getLabel(), req.getColor(), req.getPlType())
        ));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @Data
    public static class UpsertRequest {
        private String label;
        private String color;
        private String plType;
    }
}
