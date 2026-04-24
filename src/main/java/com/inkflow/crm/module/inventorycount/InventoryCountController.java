package com.inkflow.crm.module.inventorycount;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.inventorycount.dto.*;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory/counts")
@RequiredArgsConstructor
public class InventoryCountController {

    private final InventoryCountService countService;

    @GetMapping
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<PageResult<InventoryCountDto>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<InventoryCountDto> result = countService.getAll(page, size);
        return ResponseEntity.ok(ApiResponse.success(new PageResult<>(result.getContent(), PaginationDto.from(result))));
    }

    @GetMapping("/{id}")
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<InventoryCountDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(countService.getById(id)));
    }

    @PostMapping
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<InventoryCountDto>> create(@RequestBody CreateInventoryCountRequest req) {
        return ResponseEntity.ok(ApiResponse.success(countService.create(req)));
    }

    @PatchMapping("/{id}/items")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<InventoryCountDto>> updateItems(
            @PathVariable UUID id, @RequestBody UpdateCountItemsRequest req) {
        return ResponseEntity.ok(ApiResponse.success(countService.updateItems(id, req)));
    }

    @PostMapping("/{id}/confirm")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<InventoryCountDto>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(countService.confirm(id)));
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        countService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
